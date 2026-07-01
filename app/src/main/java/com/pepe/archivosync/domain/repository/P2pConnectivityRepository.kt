package com.pepe.archivosync.domain.repository

import android.net.Uri
import com.pepe.archivosync.domain.model.P2pDevice
import com.pepe.archivosync.domain.model.P2pFileTransfer
import com.pepe.archivosync.domain.model.PeerLink
import com.pepe.archivosync.domain.model.SignalingState
import kotlinx.coroutines.flow.Flow

/**
 * The real WebRTC peer-to-peer channel backed by the orchestrator
 * (pepe-p2p-orquestrator). Responsibilities:
 *
 *  1. Ensure this device is registered (`POST /v1/devices`) and remembered.
 *  2. Open the signaling WebSocket and keep it alive.
 *  3. List the user's other devices as candidate peers.
 *  4. Establish a WebRTC DataChannel to a peer and stream files over it.
 *
 * All state is exposed as flows so the ViewModel can render it reactively.
 */
interface P2pConnectivityRepository {

    val signalingState: Flow<SignalingState>

    /** Peers currently linked (or forming a link) via WebRTC. */
    val peers: Flow<List<PeerLink>>

    /** Files moving across any DataChannel, in either direction. */
    val transfers: Flow<List<P2pFileTransfer>>

    /** Registers this device if needed, then connects the signaling socket. */
    suspend fun connect(): Result<Unit>

    /** Tears down the signaling socket and all peer connections. */
    fun disconnect()

    /** This user's other registered devices (candidate peers). */
    suspend fun listDevices(): Result<List<P2pDevice>>

    /** Opens (or reuses) a WebRTC link to [deviceId] and its DataChannel. */
    suspend fun connectToPeer(deviceId: String): Result<Unit>

    /** Sends a local file (SAF/content Uri) to [deviceId] over the DataChannel. */
    suspend fun sendFile(deviceId: String, uri: Uri): Result<Unit>
}
