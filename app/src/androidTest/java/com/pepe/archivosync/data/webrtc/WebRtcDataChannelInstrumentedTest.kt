package com.pepe.archivosync.data.webrtc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pepe.archivosync.data.remote.IceServersResponseDto
import com.pepe.archivosync.data.remote.RegisterDeviceResponseDto
import com.pepe.archivosync.domain.model.FileTransferState
import com.pepe.archivosync.domain.model.IceServer
import com.pepe.archivosync.domain.model.P2pFileTransfer
import com.pepe.archivosync.domain.model.TransferDirection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.PeerConnectionFactory
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Full-stack WebRTC instrumented test. It runs on an emulator/device (where the
 * native `libjingle_peerconnection_so` lives) and stands up TWO peers in one
 * process — each its own [SignalingClient] + [WebRtcSessionManager], registered
 * as a distinct device — that discover and connect to each other by relaying
 * SDP/ICE through the LIVE orchestrator. Once the DataChannel is open it sends a
 * file A→B and asserts the received bytes match exactly.
 *
 * Prerequisites (same live stack as SignalingE2ETest):
 *   docker compose -f docker-compose.integration.yml up --build
 *   docker compose -f docker-compose.integration.yml exec api \
 *       php database/seed.php e2e@archivosync.test e2e-password
 *
 * From an emulator the host is reachable at 10.0.2.2 (default). Override with
 * instrumentation args, e.g. `-e orchHost 192.168.1.20`. Self-skips (JUnit
 * assumption) when the stack isn't reachable.
 */
@RunWith(AndroidJUnit4::class)
class WebRtcDataChannelInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val args get() = InstrumentationRegistry.getArguments()
    private val host by lazy { args.getString("orchHost")?.takeIf { it.isNotBlank() } ?: "10.0.2.2" }
    private val apiUrl by lazy { "http://$host:9220/v1" }
    private val orchUrl by lazy { "http://$host:9224/v1" }
    private val wsUrl by lazy { "ws://$host:9224" }
    private val email by lazy { args.getString("e2eEmail")?.takeIf { it.isNotBlank() } ?: "e2e@archivosync.test" }
    private val password by lazy { args.getString("e2ePassword")?.takeIf { it.isNotBlank() } ?: "e2e-password" }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val codec = SignalCodec(json)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var factory: PeerConnectionFactory

    @Before
    fun setUp() {
        assumeTrue(
            "orchestrator stack not reachable at $host — skipping instrumented E2E",
            reachable("$orchUrl/health") && reachable("$apiUrl/health"),
        )
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    @After
    fun tearDown() {
        scope.cancel()
        if (::factory.isInitialized) runCatching { factory.dispose() }
    }

    @Test
    fun transfersAFileBetweenTwoPeersOverDataChannel() = runBlocking<Unit> {
        val token = login()
        val idA = registerDevice(token, "e2e-webrtc-A")
        val idB = registerDevice(token, "e2e-webrtc-B")
        val ice = fetchIceServers(token)

        val signalingA = SignalingClient(http, codec)
        val signalingB = SignalingClient(http, codec)
        val managerA = WebRtcSessionManager(context, factory, signalingA, json)
        val managerB = WebRtcSessionManager(context, factory, signalingB, json)
        managerA.setIceServers(ice)
        managerB.setIceServers(ice)

        // Wire signaling → session managers (mirrors P2pConnectivityRepositoryImpl.route).
        val welcomeA = wire(signalingA, managerA)
        val welcomeB = wire(signalingB, managerB)
        delay(300) // let the SharedFlow collectors subscribe before the welcome frame

        try {
            signalingA.connect(wsUrl, token, idA)
            signalingB.connect(wsUrl, token, idB)
            withTimeout(15_000) { welcomeA.await(); welcomeB.await() }

            // A initiates: creates the DataChannel + offer; B answers via the relay.
            managerA.openConnection(idB, "B")
            awaitChannelOpen(managerA, idB, timeoutMs = 60_000)

            // Send a file A → B and verify byte-for-byte on the receiving side.
            val payload = Random(1234).nextBytes(700 * 1024)
            val name = "payload-${System.currentTimeMillis()}.bin"
            val source = File(context.cacheDir, name).apply { writeBytes(payload) }

            managerA.sendFile(idB, name, payload.size.toLong(), source.inputStream())

            val received = awaitTransfer(managerB, timeoutMs = 60_000) {
                it.direction == TransferDirection.INCOMING && it.state == FileTransferState.COMPLETED
            }
            assertEquals(name, received.name)
            assertEquals(payload.size.toLong(), received.sizeBytes)
            val receivedPath = received.receivedPath
            assertNotNull("received transfer must expose a file path", receivedPath)

            val receivedBytes = File(receivedPath!!).readBytes()
            assertEquals("received size mismatch", payload.size, receivedBytes.size)
            assertArrayEquals("received bytes must match sent bytes", payload, receivedBytes)

            // Sender side should also report completion.
            val sent = awaitTransfer(managerA, timeoutMs = 10_000) {
                it.direction == TransferDirection.OUTGOING && it.state == FileTransferState.COMPLETED
            }
            assertEquals(payload.size.toLong(), sent.transferredBytes)

            source.delete()
            File(receivedPath).delete()
        } finally {
            managerA.closeAll(); managerB.closeAll()
            signalingA.close(); signalingB.close()
        }
    }

    // --- wiring ------------------------------------------------------------

    /** Routes inbound signaling into [manager]; completes when `welcome` arrives. */
    private fun wire(signaling: SignalingClient, manager: WebRtcSessionManager): CompletableDeferred<Unit> {
        val welcomed = CompletableDeferred<Unit>()
        scope.launch {
            signaling.events.collect { event ->
                when (event) {
                    is SignalEvent.Welcome -> {
                        if (event.iceServers.isNotEmpty()) manager.setIceServers(event.iceServers)
                        welcomed.complete(Unit)
                    }
                    is SignalEvent.Offer -> manager.onRemoteOffer(event.from, event.sdp, null)
                    is SignalEvent.Answer -> manager.onRemoteAnswer(event.from, event.sdp)
                    is SignalEvent.Ice -> manager.onRemoteIce(event.from, event.candidate, event.sdpMid, event.sdpMLineIndex)
                    is SignalEvent.Bye -> manager.onRemoteBye(event.from)
                    else -> Unit
                }
            }
        }
        return welcomed
    }

    private suspend fun awaitChannelOpen(manager: WebRtcSessionManager, peerId: String, timeoutMs: Long) {
        val opened = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val link = manager.peers.first().firstOrNull { it.deviceId == peerId }
                if (link?.channelOpen == true) return@withTimeoutOrNull true
                delay(200)
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        assertTrue("DataChannel to $peerId did not open within ${timeoutMs}ms", opened == true)
    }

    private suspend fun awaitTransfer(
        manager: WebRtcSessionManager,
        timeoutMs: Long,
        predicate: (P2pFileTransfer) -> Boolean,
    ): P2pFileTransfer {
        val found = withTimeoutOrNull(timeoutMs) {
            while (true) {
                manager.transfers.first().firstOrNull(predicate)?.let { return@withTimeoutOrNull it }
                delay(150)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        assertNotNull("timed out waiting for a matching file transfer", found)
        return found!!
    }

    // --- orchestrator HTTP -------------------------------------------------

    private fun login(): String {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject { put("email", email); put("password", password) },
        )
        val resp = post("$apiUrl/auth/login", null, body)
        val token = json.parseToJsonElement(resp).jsonObject["token"]?.jsonPrimitive?.content
        assertNotNull("login must return a token (is the e2e user seeded?)", token)
        return token!!
    }

    private fun registerDevice(token: String, name: String): String {
        val key = Base64.getEncoder().encodeToString(name.toByteArray())
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject { put("name", name); put("platform", "android"); put("public_key", key) },
        )
        return json.decodeFromString(RegisterDeviceResponseDto.serializer(), post("$orchUrl/devices", token, body)).id
    }

    private fun fetchIceServers(token: String): List<IceServer> =
        json.decodeFromString(IceServersResponseDto.serializer(), get("$orchUrl/ice-servers", token))
            .iceServers.map { IceServer(it.urls, it.username, it.credential) }

    private fun get(url: String, token: String?): String {
        val req = Request.Builder().url(url)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .get().build()
        http.newCall(req).execute().use { r ->
            check(r.isSuccessful) { "GET $url → ${r.code}" }
            return r.body!!.string()
        }
    }

    private fun post(url: String, token: String?, jsonBody: String): String {
        val req = Request.Builder().url(url)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            check(r.isSuccessful) { "POST $url → ${r.code}" }
            return r.body!!.string()
        }
    }

    private fun reachable(healthUrl: String): Boolean = runCatching {
        http.newCall(Request.Builder().url(healthUrl).get().build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
