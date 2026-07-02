package com.pepe.archivosync.data.webrtc

import com.pepe.archivosync.domain.model.IceServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Decoded messages arriving from the orchestrator over the signaling socket. */
sealed interface SignalEvent {
    data object Open : SignalEvent
    data class Welcome(val deviceId: String, val iceServers: List<IceServer>) : SignalEvent
    data class Offer(val from: String, val sdp: String) : SignalEvent
    data class Answer(val from: String, val sdp: String) : SignalEvent
    data class Ice(val from: String, val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int) : SignalEvent
    data class Bye(val from: String) : SignalEvent
    data class Failed(val message: String) : SignalEvent
    data class Closed(val code: Int, val reason: String) : SignalEvent
}

/**
 * Client for the orchestrator's signaling plane (see docs/referencia-api.md §C).
 * Two transports, chosen by the signaling URL scheme:
 *  - `ws://` / `wss://`  → WebSocket (the OpenSwoole orchestrator, low latency).
 *  - `http://` / `https://` → **HTTP polling** (the light orchestrator that runs
 *    on shared hosting): POST frames to `<base>/signal`, short-poll
 *    `<base>/signal?device=&since=` ~1 Hz for incoming frames.
 *
 * All framing is delegated to [SignalCodec]; this class only owns the transport.
 */
@Singleton
class SignalingClient @Inject constructor(
    private val client: OkHttpClient,
    private val codec: SignalCodec,
    private val json: Json,
) {
    private val _events = MutableSharedFlow<SignalEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SignalEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var socket: WebSocket? = null
    @Volatile private var pollJob: Job? = null
    // When set, we're in polling mode: outgoing frames are POSTed here.
    @Volatile private var pollBase: String? = null
    @Volatile private var pollBearer: String? = null
    @Volatile private var pollDevice: String? = null

    fun connect(signalingBaseUrl: String, token: String, deviceId: String) {
        close()
        val base = signalingBaseUrl.trim().trimEnd('/')
        if (base.startsWith("ws://", true) || base.startsWith("wss://", true)) {
            connectWebSocket(base, token, deviceId)
        } else {
            connectPolling(base, token, deviceId)
        }
    }

    fun close() {
        socket?.close(1000, "bye")
        socket = null
        pollJob?.cancel()
        pollJob = null
        pollBase = null
        pollBearer = null
        pollDevice = null
    }

    fun sendOffer(to: String, sdp: String) = send(codec.encodeOffer(to, sdp))
    fun sendAnswer(to: String, sdp: String) = send(codec.encodeAnswer(to, sdp))
    fun sendIce(to: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) =
        send(codec.encodeIce(to, candidate, sdpMid, sdpMLineIndex))
    fun sendBye(to: String) = send(codec.encodeBye(to))

    // --- WebSocket transport ----------------------------------------------

    private fun connectWebSocket(base: String, token: String, deviceId: String) {
        // The bearer token goes in the Authorization header (not the URL) so it
        // never lands in access logs / proxies. device_id is not secret and stays
        // in the query. See docs/seguridad.md H-3.
        val url = "$base/?device_id=${enc(deviceId)}"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", bearer(token))
            .build()
        socket = client.newWebSocket(request, Listener())
    }

    // --- HTTP-polling transport (shared-hosting light orchestrator) --------

    private fun connectPolling(base: String, token: String, deviceId: String) {
        pollBase = base
        pollBearer = bearer(token)
        pollDevice = deviceId
        _events.tryEmit(SignalEvent.Open)
        pollJob = scope.launch {
            var since = 0L
            var announced = false
            while (isActive) {
                val body = runCatching { pollOnce(base, bearer(token), deviceId, since) }
                if (body.isSuccess) {
                    if (!announced) {
                        announced = true
                        // Synthetic welcome → connectivity repo flips to CONNECTED;
                        // empty fields don't clobber the ICE/device already fetched.
                        _events.tryEmit(SignalEvent.Welcome("", emptyList()))
                    }
                    runCatching {
                        val root = json.parseToJsonElement(body.getOrThrow()).jsonObject
                        root["signals"]?.jsonArray?.forEach { el ->
                            // Each signal is {id,type,from,data} — exactly what decode wants.
                            codec.decode(el.toString())?.let { _events.tryEmit(it) }
                        }
                        since = root["lastId"]?.jsonPrimitive?.long ?: since
                    }
                } else if (!announced) {
                    announced = true
                    _events.tryEmit(SignalEvent.Failed(body.exceptionOrNull()?.message ?: "signaling poll failed"))
                }
                delay(1000)
            }
        }
    }

    /** GET the mailbox; returns the response body or throws on non-2xx / IO error. */
    private fun pollOnce(base: String, bearer: String, deviceId: String, since: Long): String {
        val url = "$base/signal?device=${enc(deviceId)}&since=$since"
        val request = Request.Builder().url(url).header("Authorization", bearer).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return resp.body?.string() ?: "{}"
        }
    }

    private fun send(frame: String) {
        socket?.let { it.send(frame); return }
        val base = pollBase ?: return
        val bearer = pollBearer ?: return
        val device = pollDevice ?: return
        scope.launch { runCatching { postSignal(base, bearer, device, frame) } }
    }

    /** POST an outgoing frame with our `from` device injected. */
    private fun postSignal(base: String, bearer: String, device: String, frame: String) {
        val obj = json.parseToJsonElement(frame).jsonObject
        val withFrom = buildJsonObject {
            obj.forEach { (k, v) -> put(k, v) }
            put("from", device)
        }
        val payload = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), withFrom)
        val request = Request.Builder()
            .url("$base/signal")
            .header("Authorization", bearer)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { /* fire-and-forget */ }
    }

    private fun bearer(token: String): String =
        if (token.trim().startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _events.tryEmit(SignalEvent.Open)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = codec.decode(text) ?: return
            _events.tryEmit(event)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            _events.tryEmit(SignalEvent.Closed(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _events.tryEmit(SignalEvent.Failed(t.message ?: "signaling failure"))
        }
    }
}
