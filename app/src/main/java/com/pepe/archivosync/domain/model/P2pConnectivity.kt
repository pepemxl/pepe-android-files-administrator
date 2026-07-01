package com.pepe.archivosync.domain.model

/**
 * WebRTC/orchestrator connectivity domain. This is the *real* peer-to-peer layer
 * (as opposed to the simulated swarm dashboard in [P2pTransfer]): the app fetches
 * ICE servers, registers itself as a device, signals over a WebSocket and opens
 * a DataChannel to another device to move files directly.
 */

/** A single ICE server entry, as served by GET /v1/ice-servers. */
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null,
)

/** A device registered at the orchestrator (this user's fleet). */
data class P2pDevice(
    val id: String,
    val name: String,
    val platform: String,
    val lastSeenAt: String? = null,
)

/** State of the signaling WebSocket to the orchestrator. */
enum class SignalingState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** Lifecycle of a single peer WebRTC/DataChannel link. */
enum class PeerState { NEW, CONNECTING, CONNECTED, FAILED, CLOSED }

/** A live (or forming) link to one remote device. */
data class PeerLink(
    val deviceId: String,
    val name: String? = null,
    val state: PeerState = PeerState.NEW,
    /** True when the DataChannel is open and ready to carry files. */
    val channelOpen: Boolean = false,
)

enum class TransferDirection { INCOMING, OUTGOING }
enum class FileTransferState { NEGOTIATING, TRANSFERRING, COMPLETED, FAILED }

/**
 * One file crossing a DataChannel, in either direction. [receivedPath] is set for
 * finished incoming files (an app-private file URI/path the UI can open/share).
 */
data class P2pFileTransfer(
    val id: String,
    val peerId: String,
    val name: String,
    val sizeBytes: Long,
    val transferredBytes: Long,
    val direction: TransferDirection,
    val state: FileTransferState,
    val receivedPath: String? = null,
    val error: String? = null,
) {
    val progress: Int
        get() = if (sizeBytes <= 0) 0 else ((transferredBytes * 100) / sizeBytes).toInt().coerceIn(0, 100)
}
