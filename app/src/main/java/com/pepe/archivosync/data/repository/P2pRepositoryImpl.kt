package com.pepe.archivosync.data.repository

import android.net.Uri
import com.pepe.archivosync.domain.model.FileKind
import com.pepe.archivosync.domain.model.FileNode
import com.pepe.archivosync.domain.model.FileTransferState
import com.pepe.archivosync.domain.model.P2pFileTransfer
import com.pepe.archivosync.domain.model.P2pMode
import com.pepe.archivosync.domain.model.P2pStatus
import com.pepe.archivosync.domain.model.P2pTransfer
import com.pepe.archivosync.domain.model.PeerLink
import com.pepe.archivosync.domain.model.TransferDirection
import com.pepe.archivosync.domain.repository.P2pConnectivityRepository
import com.pepe.archivosync.domain.repository.P2pRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Projects the live WebRTC DataChannel activity ([P2pConnectivityRepository])
 * into the BitTorrent-style dashboard model. A 1 Hz ticker resamples so speeds
 * are real (bytes moved since the last tick) and decay to zero when idle.
 */
@Singleton
class P2pRepositoryImpl @Inject constructor(
    private val connectivity: P2pConnectivityRepository,
    scope: CoroutineScope,
) : P2pRepository {

    /** id → bytes seen at the previous sample, for rate deltas. */
    private val lastBytes = mutableMapOf<String, Long>()
    private var lastSampleAt = 0L

    private val ticker = flow {
        while (true) { emit(Unit); delay(1_000) }
    }

    private val projected: StateFlow<List<P2pTransfer>> =
        combine(connectivity.transfers, connectivity.peers, ticker) { transfers, peers, _ ->
            project(transfers, peers)
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun observeTransfers(): Flow<List<P2pTransfer>> = projected

    override suspend fun seed(files: List<FileNode>) {
        if (files.isEmpty()) return
        val openPeers = connectivity.peers.first().filter { it.channelOpen }
        for (peer in openPeers) {
            for (file in files) {
                connectivity.sendFile(peer.deviceId, Uri.parse(file.id))
            }
        }
    }

    private fun project(transfers: List<P2pFileTransfer>, peers: List<PeerLink>): List<P2pTransfer> {
        val now = System.currentTimeMillis()
        val dt = ((now - lastSampleAt).coerceIn(1, 10_000)) / 1000.0
        lastSampleAt = now
        val names = peers.associate { it.deviceId to (it.name ?: it.deviceId) }
        lastBytes.keys.retainAll(transfers.map { it.id }.toSet())

        return transfers.map { t ->
            val prev = lastBytes[t.id] ?: t.transferredBytes
            val delta = (t.transferredBytes - prev).coerceAtLeast(0)
            lastBytes[t.id] = t.transferredBytes
            val running = t.state == FileTransferState.TRANSFERRING || t.state == FileTransferState.NEGOTIATING
            val rateKbps = if (running) ((delta / dt) / 1024.0).toLong() else 0L
            val outgoing = t.direction == TransferDirection.OUTGOING
            P2pTransfer(
                id = t.id,
                name = t.name,
                kind = FileKind.fromName(t.name),
                sizeBytes = t.sizeBytes,
                transferredBytes = t.transferredBytes,
                mode = if (outgoing) P2pMode.SEED else P2pMode.LEECH,
                progress = t.progress,
                upRateKbps = if (outgoing) rateKbps else 0L,
                downRateKbps = if (!outgoing) rateKbps else 0L,
                peerName = names[t.peerId] ?: t.peerId,
                status = when (t.state) {
                    FileTransferState.COMPLETED -> P2pStatus.DONE
                    FileTransferState.FAILED -> P2pStatus.FAILED
                    else -> P2pStatus.ACTIVE
                },
            )
        }
    }
}
