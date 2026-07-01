package com.pepe.archivosync.data.webrtc

import com.pepe.archivosync.domain.model.IceServer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
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
 * Thin WebSocket client for the orchestrator's signaling plane
 * (see docs/referencia-api.md §C). Connects to `wss://host/?token=&device_id=`,
 * decodes `{type,from,data}` frames into [SignalEvent]s and exposes typed
 * senders for offer/answer/ice/bye. One socket at a time. All framing is
 * delegated to [SignalCodec] so this class only owns the transport.
 */
@Singleton
class SignalingClient @Inject constructor(
    private val client: OkHttpClient,
    private val codec: SignalCodec,
) {
    private val _events = MutableSharedFlow<SignalEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SignalEvent> = _events.asSharedFlow()

    @Volatile private var socket: WebSocket? = null

    fun connect(signalingBaseUrl: String, token: String, deviceId: String) {
        close()
        val base = signalingBaseUrl.trimEnd('/')
        val url = "$base/?token=${enc(token)}&device_id=${enc(deviceId)}"
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, Listener())
    }

    fun close() {
        socket?.close(1000, "bye")
        socket = null
    }

    fun sendOffer(to: String, sdp: String) = send(codec.encodeOffer(to, sdp))
    fun sendAnswer(to: String, sdp: String) = send(codec.encodeAnswer(to, sdp))
    fun sendIce(to: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) =
        send(codec.encodeIce(to, candidate, sdpMid, sdpMLineIndex))
    fun sendBye(to: String) = send(codec.encodeBye(to))

    private fun send(frame: String) {
        socket?.send(frame)
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

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
