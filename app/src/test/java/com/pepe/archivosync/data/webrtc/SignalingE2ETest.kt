package com.pepe.archivosync.data.webrtc

import com.pepe.archivosync.data.remote.DevicesResponseDto
import com.pepe.archivosync.data.remote.IceServersResponseDto
import com.pepe.archivosync.data.remote.RegisterDeviceResponseDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * End-to-end signaling test against a LIVE orchestrator
 * (docker-compose.integration.yml). It drives the app's real client stack —
 * [SignalingClient] + [SignalCodec] + the orchestrator DTOs — through the full
 * flow: login → register two devices → ICE servers → list → open two WebSockets
 * → relay offer/answer/ice between them.
 *
 * It is self-skipping: if the stack isn't reachable on the expected ports the
 * whole test is assumed-away (JUnit "skipped"), so it never breaks a normal
 * `testDebugUnitTest` run. Bring the stack up and seed the user first:
 *
 *   docker compose -f docker-compose.integration.yml up --build
 *   docker compose -f docker-compose.integration.yml exec api \
 *       php database/seed.php e2e@archivosync.test e2e-password
 *
 * Endpoints/credentials are overridable via env vars (P2P_E2E_*).
 */
class SignalingE2ETest {

    private val apiUrl = env("P2P_E2E_API", "http://localhost:9220/v1")
    private val orchUrl = env("P2P_E2E_ORCH", "http://localhost:9224/v1")
    private val wsUrl = env("P2P_E2E_WS", "ws://localhost:9224")
    private val email = env("P2P_E2E_EMAIL", "e2e@archivosync.test")
    private val password = env("P2P_E2E_PASSWORD", "e2e-password")

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val codec = SignalCodec(json)

    @Before
    fun requireLiveStack() {
        assumeTrue("orchestrator stack not reachable — skipping E2E", reachable("$orchUrl/health") && reachable("$apiUrl/health"))
    }

    @Test
    fun `registers devices, fetches ICE servers and lists them`() {
        val token = login()

        val idA = registerDevice(token, "e2e-A")
        val idB = registerDevice(token, "e2e-B")
        assertNotNull(idA); assertNotNull(idB)
        assertFalse("device ids must differ", idA == idB)

        val ice = json.decodeFromString(IceServersResponseDto.serializer(), get("$orchUrl/ice-servers", token))
        assertTrue("expected at least one ICE server", ice.iceServers.isNotEmpty())
        assertTrue("ICE server must carry urls", ice.iceServers.first().urls.isNotEmpty())

        val devices = json.decodeFromString(DevicesResponseDto.serializer(), get("$orchUrl/devices", token))
        val ids = devices.devices.map { it.id }
        assertTrue("device list must contain both registered devices", ids.containsAll(listOf(idA, idB)))
    }

    @Test
    fun `relays offer, answer and ice between two peers over websocket`() = runBlocking {
        val token = login()
        val idA = registerDevice(token, "e2e-A")
        val idB = registerDevice(token, "e2e-B")

        val scope = CoroutineScope(Dispatchers.IO)
        val clientA = SignalingClient(http, codec, json)
        val clientB = SignalingClient(http, codec, json)
        val eventsA = record(scope, clientA)
        val eventsB = record(scope, clientB)

        try {
            // Subscriptions must be live before the welcome frame arrives.
            delay(300)
            clientA.connect(wsUrl, token, idA)
            clientB.connect(wsUrl, token, idB)

            val welcomeA = awaitEvent(eventsA) { it is SignalEvent.Welcome } as SignalEvent.Welcome
            val welcomeB = awaitEvent(eventsB) { it is SignalEvent.Welcome } as SignalEvent.Welcome
            assertEquals(idA, welcomeA.deviceId)
            assertEquals(idB, welcomeB.deviceId)
            assertTrue("welcome should carry ICE servers", welcomeA.iceServers.isNotEmpty())

            // A → B offer
            val offerSdp = "v=0\r\no=- 111 2 IN IP4 127.0.0.1\r\ns=-\r\n"
            clientA.sendOffer(idB, offerSdp)
            val offer = awaitEvent(eventsB) { it is SignalEvent.Offer } as SignalEvent.Offer
            assertEquals(idA, offer.from)
            assertEquals(offerSdp, offer.sdp)

            // B → A answer
            val answerSdp = "v=0\r\no=- 222 2 IN IP4 127.0.0.1\r\ns=-\r\n"
            clientB.sendAnswer(idA, answerSdp)
            val answer = awaitEvent(eventsA) { it is SignalEvent.Answer } as SignalEvent.Answer
            assertEquals(idB, answer.from)
            assertEquals(answerSdp, answer.sdp)

            // A → B ICE candidate (trickle)
            clientA.sendIce(idB, "candidate:1 1 udp 2130706431 127.0.0.1 54321 typ host", "0", 0)
            val ice = awaitEvent(eventsB) { it is SignalEvent.Ice } as SignalEvent.Ice
            assertEquals(idA, ice.from)
            assertEquals("0", ice.sdpMid)
            assertTrue(ice.candidate.startsWith("candidate:"))
        } finally {
            clientA.close(); clientB.close(); scope.cancel()
        }
    }

    @Test
    fun `rejects a bad token on the signaling socket`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO)
        val client = SignalingClient(http, codec, json)
        val events = record(scope, client)
        try {
            delay(200)
            client.connect(wsUrl, "not-a-valid-token", "00000000-0000-0000-0000-000000000000")
            // Server pushes an error frame and/or closes with 4001.
            val failure = awaitEvent(events, timeoutMs = 6000) {
                it is SignalEvent.Failed || it is SignalEvent.Closed
            }
            assertNotNull(failure)
        } finally {
            client.close(); scope.cancel()
        }
    }

    // --- helpers -----------------------------------------------------------

    private fun record(scope: CoroutineScope, client: SignalingClient): MutableList<SignalEvent> {
        val events = Collections.synchronizedList(mutableListOf<SignalEvent>())
        scope.launch { client.events.collect { events.add(it) } }
        return events
    }

    private suspend fun awaitEvent(
        events: List<SignalEvent>,
        timeoutMs: Long = 8000,
        predicate: (SignalEvent) -> Boolean,
    ): SignalEvent {
        val found = withTimeoutOrNull(timeoutMs) {
            while (true) {
                synchronized(events) { events.firstOrNull(predicate) }?.let { return@withTimeoutOrNull it }
                delay(50)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        assertNotNull("timed out waiting for a matching signaling event", found)
        return found!!
    }

    private fun login(): String {
        val body = buildJsonObject { put("email", email); put("password", password) }
        val resp = post("$apiUrl/auth/login", null, json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body))
        val token = json.parseToJsonElement(resp).jsonObject["token"]?.jsonPrimitive?.content
        assertNotNull("login must return a token (is the e2e user seeded?)", token)
        return token!!
    }

    private fun registerDevice(token: String, name: String): String {
        val key = Base64.getEncoder().encodeToString(name.toByteArray())
        val body = buildJsonObject {
            put("name", name); put("platform", "android"); put("public_key", key)
        }
        val resp = post("$orchUrl/devices", token, json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), body))
        return json.decodeFromString(RegisterDeviceResponseDto.serializer(), resp).id
    }

    private fun bearer(token: String?) = token?.let { "Bearer $it" }

    private fun get(url: String, token: String?): String {
        val req = Request.Builder().url(url)
            .apply { bearer(token)?.let { header("Authorization", it) } }
            .get().build()
        http.newCall(req).execute().use { r ->
            check(r.isSuccessful) { "GET $url → ${r.code}" }
            return r.body!!.string()
        }
    }

    private fun post(url: String, token: String?, jsonBody: String): String {
        val req = Request.Builder().url(url)
            .apply { bearer(token)?.let { header("Authorization", it) } }
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            check(r.isSuccessful) { "POST $url → ${r.code}: ${r.body?.string()}" }
            return r.body!!.string()
        }
    }

    private fun reachable(healthUrl: String): Boolean = runCatching {
        val req = Request.Builder().url(healthUrl).get().build()
        http.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun env(name: String, default: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
}
