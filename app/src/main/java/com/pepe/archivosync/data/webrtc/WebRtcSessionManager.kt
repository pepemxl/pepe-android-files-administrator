package com.pepe.archivosync.data.webrtc

import android.content.Context
import com.pepe.archivosync.domain.model.FileTransferState
import com.pepe.archivosync.domain.model.IceServer
import com.pepe.archivosync.domain.model.P2pFileTransfer
import com.pepe.archivosync.domain.model.PeerLink
import com.pepe.archivosync.domain.model.PeerState
import com.pepe.archivosync.domain.model.TransferDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the WebRTC layer: one [PeerConnection] + DataChannel per remote device,
 * SDP/ICE negotiation (offer/answer relayed through [SignalingClient]) and a
 * simple chunked file protocol over the channel.
 *
 * File framing on the DataChannel:
 *  - text  `{"t":"meta","id":..,"name":..,"size":..}`  — start of a file
 *  - binary chunks (16 KiB)                            — the bytes, in order
 *  - text  `{"t":"end","id":..}`                       — file complete
 *
 * The channel is reliable + ordered (DataChannel defaults), so chunks never
 * overtake their meta/end markers.
 */
@Singleton
class WebRtcSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val factory: PeerConnectionFactory,
    private val signaling: SignalingClient,
    private val json: Json,
) {
    private companion object {
        const val CHUNK = 16 * 1024
        const val MAX_BUFFERED = 8L * 1024 * 1024   // pause sending above this
        const val PROGRESS_STEP = 256L * 1024        // emit progress every 256 KiB
        const val LABEL = "files"
    }

    private var iceServers: List<PeerConnection.IceServer> = emptyList()
    private val sessions = ConcurrentHashMap<String, Session>()

    private val _peers = MutableStateFlow<Map<String, PeerLink>>(emptyMap())
    val peers: Flow<List<PeerLink>> = _peers.map { it.values.toList() }

    private val _transfers = MutableStateFlow<Map<String, P2pFileTransfer>>(emptyMap())
    val transfers: Flow<List<P2pFileTransfer>> = _transfers.map { it.values.sortedByDescending { t -> t.id } }

    fun setIceServers(servers: List<IceServer>) {
        iceServers = servers.map { s ->
            PeerConnection.IceServer.builder(s.urls)
                .apply {
                    if (!s.username.isNullOrBlank()) setUsername(s.username)
                    if (!s.credential.isNullOrBlank()) setPassword(s.credential)
                }
                .createIceServer()
        }
    }

    fun peerNames(names: Map<String, String>) {
        _peers.update { current ->
            current.mapValues { (id, link) -> link.copy(name = names[id] ?: link.name) }
        }
    }

    // --- Connection setup --------------------------------------------------

    /** Caller (offerer): create the connection + DataChannel and send an offer. */
    suspend fun openConnection(peerId: String, peerName: String?) {
        val session = sessions.getOrPut(peerId) { newSession(peerId, peerName) }
        if (session.dataChannel == null) {
            val dc = session.pc.createDataChannel(LABEL, DataChannel.Init())
            session.attachChannel(dc)
        }
        updatePeer(peerId) { it.copy(state = PeerState.CONNECTING) }
        val offer = session.pc.createOfferSdp()
        session.pc.setLocalSdp(offer)
        signaling.sendOffer(peerId, offer.description)
    }

    /** Callee: an offer arrived — answer it. The DataChannel arrives via observer. */
    suspend fun onRemoteOffer(peerId: String, sdp: String, peerName: String?) {
        val session = sessions.getOrPut(peerId) { newSession(peerId, peerName) }
        updatePeer(peerId) { it.copy(state = PeerState.CONNECTING) }
        session.pc.setRemoteSdp(SessionDescription(SessionDescription.Type.OFFER, sdp))
        session.remoteSet = true
        session.flushCandidates()
        val answer = session.pc.createAnswerSdp()
        session.pc.setLocalSdp(answer)
        signaling.sendAnswer(peerId, answer.description)
    }

    suspend fun onRemoteAnswer(peerId: String, sdp: String) {
        val session = sessions[peerId] ?: return
        session.pc.setRemoteSdp(SessionDescription(SessionDescription.Type.ANSWER, sdp))
        session.remoteSet = true
        session.flushCandidates()
    }

    fun onRemoteIce(peerId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val session = sessions[peerId] ?: return
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        if (session.remoteSet) session.pc.addIceCandidate(ice) else session.pending += ice
    }

    fun onRemoteBye(peerId: String) = close(peerId)

    fun close(peerId: String) {
        sessions.remove(peerId)?.dispose()
        updatePeer(peerId) { it.copy(state = PeerState.CLOSED, channelOpen = false) }
    }

    fun closeAll() {
        sessions.keys.toList().forEach { close(it) }
        sessions.clear()
    }

    // --- File transfer -----------------------------------------------------

    /** Streams a file over the (open) DataChannel to [peerId]. */
    suspend fun sendFile(peerId: String, name: String, size: Long, input: InputStream) = withContext(Dispatchers.IO) {
        val session = sessions[peerId] ?: error("no session for $peerId")
        val channel = awaitOpenChannel(session) ?: error("DataChannel not open")
        val transferId = "out_${System.currentTimeMillis()}"
        putTransfer(transferId, peerId, name, size, TransferDirection.OUTGOING, FileTransferState.TRANSFERRING, 0)

        try {
            channel.send(textBuffer(buildJsonObject {
                put("t", "meta"); put("id", transferId); put("name", name); put("size", size)
            }))
            input.use { stream ->
                val buffer = ByteArray(CHUNK)
                var sent = 0L
                var lastEmit = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    while (channel.bufferedAmount() > MAX_BUFFERED) delay(10)
                    channel.send(DataChannel.Buffer(ByteBuffer.wrap(buffer, 0, read), true))
                    sent += read
                    if (sent - lastEmit >= PROGRESS_STEP) {
                        lastEmit = sent
                        updateTransfer(transferId) { it.copy(transferredBytes = sent) }
                    }
                }
                channel.send(textBuffer(buildJsonObject { put("t", "end"); put("id", transferId) }))
                updateTransfer(transferId) {
                    it.copy(transferredBytes = size, state = FileTransferState.COMPLETED)
                }
            }
        } catch (t: Throwable) {
            updateTransfer(transferId) { it.copy(state = FileTransferState.FAILED, error = t.message) }
            throw t
        }
    }

    private suspend fun awaitOpenChannel(session: Session): DataChannel? {
        repeat(300) { // ~30s
            val dc = session.dataChannel
            if (dc != null && dc.state() == DataChannel.State.OPEN) return dc
            delay(100)
        }
        return null
    }

    // --- Session plumbing --------------------------------------------------

    private fun newSession(peerId: String, peerName: String?): Session {
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val session = Session(peerId)
        val pc = factory.createPeerConnection(config, PeerObserver(session))
            ?: error("createPeerConnection returned null")
        session.pc = pc
        updatePeer(peerId) { PeerLink(peerId, peerName, PeerState.NEW) }
        return session
    }

    private inner class Session(val peerId: String) {
        lateinit var pc: PeerConnection
        var dataChannel: DataChannel? = null
        var remoteSet: Boolean = false
        val pending = mutableListOf<IceCandidate>()

        // incoming file assembly
        private var recvId: String? = null
        private var recvName: String? = null
        private var recvSize: Long = 0
        private var recvBytes: Long = 0
        private var recvOut: FileOutputStream? = null
        private var recvLastEmit: Long = 0

        fun attachChannel(dc: DataChannel) {
            dataChannel = dc
            dc.registerObserver(ChannelObserver(this, dc))
        }

        fun flushCandidates() {
            pending.forEach { pc.addIceCandidate(it) }
            pending.clear()
        }

        fun onControl(obj: JsonObject) {
            when (obj["t"]?.jsonPrimitive?.content) {
                "meta" -> {
                    recvId = obj["id"]?.jsonPrimitive?.content
                    recvName = obj["name"]?.jsonPrimitive?.content ?: "file"
                    recvSize = obj["size"]?.jsonPrimitive?.long ?: 0
                    recvBytes = 0
                    recvLastEmit = 0
                    val dir = File(context.filesDir, "p2p_incoming").apply { mkdirs() }
                    val out = File(dir, sanitize(recvName!!))
                    recvOut = FileOutputStream(out)
                    recvId?.let {
                        putTransfer(it, peerId, recvName!!, recvSize, TransferDirection.INCOMING, FileTransferState.TRANSFERRING, 0)
                    }
                }
                "end" -> finishReceive()
            }
        }

        fun onBinary(bytes: ByteArray) {
            val out = recvOut ?: return
            out.write(bytes)
            recvBytes += bytes.size
            val id = recvId ?: return
            if (recvBytes - recvLastEmit >= PROGRESS_STEP) {
                recvLastEmit = recvBytes
                updateTransfer(id) { it.copy(transferredBytes = recvBytes) }
            }
        }

        private fun finishReceive() {
            val out = recvOut ?: return
            runCatching { out.flush(); out.close() }
            val id = recvId
            val name = recvName
            val path = name?.let { File(File(context.filesDir, "p2p_incoming"), sanitize(it)).absolutePath }
            if (id != null) {
                updateTransfer(id) {
                    it.copy(transferredBytes = recvSize, state = FileTransferState.COMPLETED, receivedPath = path)
                }
            }
            recvOut = null; recvId = null; recvName = null; recvBytes = 0
        }

        fun dispose() {
            runCatching { recvOut?.close() }
            runCatching { dataChannel?.dispose() }
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }

        private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private inner class PeerObserver(private val session: Session) : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            signaling.sendIce(session.peerId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
        }

        override fun onDataChannel(dc: DataChannel) {
            session.attachChannel(dc)
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            val mapped = when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> PeerState.CONNECTED
                PeerConnection.PeerConnectionState.CONNECTING -> PeerState.CONNECTING
                PeerConnection.PeerConnectionState.FAILED -> PeerState.FAILED
                PeerConnection.PeerConnectionState.CLOSED,
                PeerConnection.PeerConnectionState.DISCONNECTED -> PeerState.CLOSED
                else -> PeerState.NEW
            }
            updatePeer(session.peerId) { it.copy(state = mapped) }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {}
        override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    }

    private inner class ChannelObserver(
        private val session: Session,
        private val channel: DataChannel,
    ) : DataChannel.Observer {
        override fun onStateChange() {
            val open = channel.state() == DataChannel.State.OPEN
            updatePeer(session.peerId) { it.copy(channelOpen = open) }
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            val data = buffer.data
            val bytes = ByteArray(data.remaining())
            data.get(bytes)
            if (buffer.binary) {
                session.onBinary(bytes)
            } else {
                val text = String(bytes, StandardCharsets.UTF_8)
                runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()?.let(session::onControl)
            }
        }

        override fun onBufferedAmountChange(previousAmount: Long) {}
    }

    // --- State helpers -----------------------------------------------------

    private fun textBuffer(obj: JsonObject): DataChannel.Buffer {
        val bytes = json.encodeToString(JsonObject.serializer(), obj).toByteArray(StandardCharsets.UTF_8)
        return DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
    }

    private fun updatePeer(peerId: String, transform: (PeerLink) -> PeerLink) {
        _peers.update { current ->
            val existing = current[peerId] ?: PeerLink(peerId)
            current + (peerId to transform(existing))
        }
    }

    private fun putTransfer(
        id: String, peerId: String, name: String, size: Long,
        direction: TransferDirection, state: FileTransferState, transferred: Long,
    ) {
        _transfers.update { it + (id to P2pFileTransfer(id, peerId, name, size, transferred, direction, state)) }
    }

    private fun updateTransfer(id: String, transform: (P2pFileTransfer) -> P2pFileTransfer) {
        _transfers.update { current ->
            val existing = current[id] ?: return@update current
            current + (id to transform(existing))
        }
    }
}
