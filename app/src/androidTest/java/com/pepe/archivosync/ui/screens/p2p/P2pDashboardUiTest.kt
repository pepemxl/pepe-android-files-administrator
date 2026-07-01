package com.pepe.archivosync.ui.screens.p2p

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pepe.archivosync.data.remote.IceServersResponseDto
import com.pepe.archivosync.data.remote.OrchestratorApi
import com.pepe.archivosync.data.remote.RegisterDeviceResponseDto
import com.pepe.archivosync.data.repository.P2pConnectivityRepositoryImpl
import com.pepe.archivosync.data.repository.P2pRepositoryImpl
import com.pepe.archivosync.data.webrtc.SignalCodec
import com.pepe.archivosync.data.webrtc.SignalEvent
import com.pepe.archivosync.data.webrtc.SignalingClient
import com.pepe.archivosync.data.webrtc.WebRtcSessionManager
import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.IceServer
import com.pepe.archivosync.domain.model.P2pMode
import com.pepe.archivosync.domain.model.P2pStatus
import com.pepe.archivosync.domain.model.P2pTransfer
import com.pepe.archivosync.domain.model.SignalingState
import com.pepe.archivosync.domain.repository.P2pConnectivityRepository
import com.pepe.archivosync.domain.repository.SettingsRepository
import com.pepe.archivosync.ui.theme.ArchivoSyncTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.PeerConnectionFactory
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Dashboard end-to-end with two linked devices. Device A is the production stack
 * — [P2pConnectivityRepositoryImpl] + [P2pRepositoryImpl] + the real
 * [P2pViewModel] — device B a second in-process peer, both linked through the
 * LIVE orchestrator. A real file moves A→B (a completed SEED) and B→A (a LEECH).
 *
 *  - [dashboardReflectsRealSeedAndLeech] asserts the ViewModel's [P2pUiState]
 *    (portable across API levels).
 *  - [dashboardScreenRendersSeedAndLeech] renders the actual [P2pScreen] Compose
 *    UI, waits for the transfer cards to appear on screen and saves a screenshot
 *    (needs API ≤ 34: Compose's Espresso sync throws on API 36's InputManager).
 *
 * Prereqs/skip are identical to WebRtcDataChannelInstrumentedTest.
 */
@RunWith(AndroidJUnit4::class)
class P2pDashboardUiTest {

    @get:Rule
    val compose = createComposeRule()

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
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val codec = SignalCodec(json)
    private val api: OrchestratorApi by lazy {
        Retrofit.Builder().baseUrl("http://localhost/").client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(OrchestratorApi::class.java)
    }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var factory: PeerConnectionFactory
    private var cleanup: (() -> Unit)? = null

    @Before
    fun setUp() {
        assumeTrue(
            "orchestrator stack not reachable at $host — skipping dashboard UI E2E",
            reachable("$orchUrl/health") && reachable("$apiUrl/health"),
        )
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    @After
    fun tearDown() {
        runCatching { cleanup?.invoke() }
        appScope.cancel()
        if (::factory.isInitialized) runCatching { factory.dispose() }
    }

    /** A linked A↔B pair, with A being the real dashboard stack. */
    private class Fixture(
        val viewModel: P2pViewModel,
        val connectivityA: P2pConnectivityRepository,
        val webrtcB: WebRtcSessionManager,
        val idA: String,
        val idB: String,
    )

    private suspend fun linkTwoDevices(): Fixture {
        val settingsA = FakeSettings(
            AppSettings(orchestratorUrl = orchUrl, signalingUrl = wsUrl, token = "", deviceName = "dash-A")
        )
        val signalingA = SignalingClient(http, codec)
        val webrtcA = WebRtcSessionManager(context, factory, signalingA, json)
        val connectivityA = P2pConnectivityRepositoryImpl(context, api, signalingA, webrtcA, settingsA, appScope)
        val dashboardA = P2pRepositoryImpl(connectivityA, appScope)
        val viewModel = P2pViewModel(dashboardA, connectivityA, settingsA)

        val signalingB = SignalingClient(http, codec)
        val webrtcB = WebRtcSessionManager(context, factory, signalingB, json)

        val token = login()
        settingsA.set { it.copy(token = token) }
        val idB = registerDevice(token, "dash-B")
        webrtcB.setIceServers(fetchIceServers(token))
        val welcomeB = wire(signalingB, webrtcB)

        connectivityA.connect().getOrThrow()
        withTimeout(15_000) { connectivityA.signalingState.first { it == SignalingState.CONNECTED } }
        val idA = settingsA.settings.first().deviceId
        require(idA.isNotBlank()) { "device A did not register" }

        signalingB.connect(wsUrl, token, idB)
        withTimeout(15_000) { welcomeB.await() }

        connectivityA.connectToPeer(idB).getOrThrow()
        awaitChannelOpen(connectivityA, idB, 60_000)

        cleanup = { connectivityA.disconnect(); signalingB.close(); webrtcB.closeAll() }
        return Fixture(viewModel, connectivityA, webrtcB, idA, idB)
    }

    @Test
    fun dashboardReflectsRealSeedAndLeech() = runBlocking<Unit> {
        val seedName = "seed-${System.currentTimeMillis()}.bin"
        val leechName = "leech-${System.currentTimeMillis()}.bin"
        val f = linkTwoDevices()
        val stateJob = appScope.launch { f.viewModel.state.collect {} }

        sendSeed(f, seedName)
        awaitUiTransfer(f.viewModel, 30_000) {
            it.name == seedName && it.mode == P2pMode.SEED && it.progress == 100 && it.status == P2pStatus.DONE
        }
        sendLeech(f, leechName)
        awaitUiTransfer(f.viewModel, 30_000) {
            it.name == leechName && it.mode == P2pMode.LEECH && it.progress == 100 && it.status == P2pStatus.DONE
        }
        assertTrue("peer count must reflect the open link", f.viewModel.state.value.stats.peers >= 1)
        stateJob.cancel()
    }

    @Test
    fun dashboardScreenRendersSeedAndLeech() {
        val seedName = "seed-${System.currentTimeMillis()}.bin"
        val leechName = "leech-${System.currentTimeMillis()}.bin"

        val f = runBlocking { linkTwoDevices() }

        // Render the real dashboard screen bound to the real ViewModel.
        compose.setContent { ArchivoSyncTheme { P2pScreen(f.viewModel) } }

        runBlocking { sendSeed(f, seedName) }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(seedName, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        runBlocking { sendLeech(f, leechName) }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText(leechName, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Prove pixels were rendered: capture the screen to a PNG.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("screen must render with real pixels", bitmap.width > 0 && bitmap.height > 0)
        val shot = File(context.filesDir, "p2p_dashboard.png")
        FileOutputStream(shot).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("P2P dashboard screenshot saved to ${shot.absolutePath} (${bitmap.width}x${bitmap.height})")
    }

    // --- transfer triggers -------------------------------------------------

    private suspend fun sendSeed(f: Fixture, name: String) {
        val file = File(context.cacheDir, name).apply { writeBytes(Random(7).nextBytes(400 * 1024)) }
        f.connectivityA.sendFile(f.idB, Uri.fromFile(file)).getOrThrow()
    }

    private suspend fun sendLeech(f: Fixture, name: String) {
        val payload = Random(9).nextBytes(300 * 1024)
        f.webrtcB.sendFile(f.idA, name, payload.size.toLong(), ByteArrayInputStream(payload))
    }

    // --- helpers -----------------------------------------------------------

    private class FakeSettings(initial: AppSettings) : SettingsRepository {
        private val flow = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = flow
        override suspend fun update(transform: (AppSettings) -> AppSettings) { flow.value = transform(flow.value) }
        fun set(transform: (AppSettings) -> AppSettings) { flow.value = transform(flow.value) }
    }

    private suspend fun awaitUiTransfer(
        viewModel: P2pViewModel,
        timeoutMs: Long,
        predicate: (P2pTransfer) -> Boolean,
    ) {
        val found = withTimeoutOrNull(timeoutMs) {
            while (true) {
                viewModel.state.value.transfers.firstOrNull(predicate)?.let { return@withTimeoutOrNull it }
                delay(150)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        assertTrue("dashboard UI state never showed the expected transfer", found != null)
    }

    private fun wire(signaling: SignalingClient, manager: WebRtcSessionManager): CompletableDeferred<Unit> {
        val welcomed = CompletableDeferred<Unit>()
        appScope.launch {
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

    private suspend fun awaitChannelOpen(repo: P2pConnectivityRepository, peerId: String, timeoutMs: Long) {
        val opened = withTimeoutOrNull(timeoutMs) {
            while (true) {
                if (repo.peers.first().firstOrNull { it.deviceId == peerId }?.channelOpen == true) {
                    return@withTimeoutOrNull true
                }
                delay(200)
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        assertTrue("DataChannel to $peerId did not open in ${timeoutMs}ms", opened == true)
    }

    private fun login(): String {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            buildJsonObject { put("email", email); put("password", password) },
        )
        val resp = post("$apiUrl/auth/login", null, body)
        return json.parseToJsonElement(resp).jsonObject["token"]!!.jsonPrimitive.content
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
            .apply { token?.let { header("Authorization", "Bearer $it") } }.get().build()
        http.newCall(req).execute().use { r -> check(r.isSuccessful) { "GET $url → ${r.code}" }; return r.body!!.string() }
    }

    private fun post(url: String, token: String?, jsonBody: String): String {
        val req = Request.Builder().url(url)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .post(jsonBody.toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r -> check(r.isSuccessful) { "POST $url → ${r.code}" }; return r.body!!.string() }
    }

    private fun reachable(healthUrl: String): Boolean = runCatching {
        http.newCall(Request.Builder().url(healthUrl).get().build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
}
