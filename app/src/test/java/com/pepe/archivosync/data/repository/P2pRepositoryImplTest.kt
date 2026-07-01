package com.pepe.archivosync.data.repository

import android.net.Uri
import com.pepe.archivosync.domain.model.FileTransferState
import com.pepe.archivosync.domain.model.P2pDevice
import com.pepe.archivosync.domain.model.P2pFileTransfer
import com.pepe.archivosync.domain.model.P2pMode
import com.pepe.archivosync.domain.model.P2pStatus
import com.pepe.archivosync.domain.model.PeerLink
import com.pepe.archivosync.domain.model.PeerState
import com.pepe.archivosync.domain.model.SignalingState
import com.pepe.archivosync.domain.model.TransferDirection
import com.pepe.archivosync.domain.repository.P2pConnectivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the BitTorrent-style dashboard projection: [P2pRepositoryImpl] maps
 * live WebRTC [P2pFileTransfer]s (from a fake [P2pConnectivityRepository]) into
 * the dashboard's [com.pepe.archivosync.domain.model.P2pTransfer] shape.
 */
class P2pRepositoryImplTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    private class FakeConnectivity(
        transfers: List<P2pFileTransfer>,
        peers: List<PeerLink>,
    ) : P2pConnectivityRepository {
        override val signalingState: Flow<SignalingState> = MutableStateFlow(SignalingState.CONNECTED)
        override val peers = MutableStateFlow(peers)
        override val transfers = MutableStateFlow(transfers)
        val sent = mutableListOf<String>()
        override suspend fun connect() = Result.success(Unit)
        override fun disconnect() {}
        override suspend fun listDevices() = Result.success(emptyList<P2pDevice>())
        override suspend fun connectToPeer(deviceId: String) = Result.success(Unit)
        override suspend fun sendFile(deviceId: String, uri: Uri): Result<Unit> {
            sent.add(deviceId); return Result.success(Unit)
        }
    }

    @Test
    fun `projects direction, progress, state and peer name`() = runBlocking {
        val fake = FakeConnectivity(
            transfers = listOf(
                P2pFileTransfer("t1", "dev-x", "in.txt", 100, 50, TransferDirection.INCOMING, FileTransferState.TRANSFERRING),
                P2pFileTransfer("t2", "dev-y", "out.bin", 200, 200, TransferDirection.OUTGOING, FileTransferState.COMPLETED),
                P2pFileTransfer("t3", "dev-x", "bad.zip", 300, 10, TransferDirection.INCOMING, FileTransferState.FAILED),
            ),
            peers = listOf(PeerLink("dev-x", "Laptop", PeerState.CONNECTED, channelOpen = true)),
        )
        val repo = P2pRepositoryImpl(fake, scope)

        val projected = withTimeout(2_000) {
            repo.observeTransfers().first { it.size == 3 }
        }.associateBy { it.id }

        val leech = projected.getValue("t1")
        assertEquals(P2pMode.LEECH, leech.mode)
        assertEquals(50, leech.progress)
        assertEquals(P2pStatus.ACTIVE, leech.status)
        assertEquals("Laptop", leech.peerName)      // resolved from peers
        assertEquals(100L, leech.sizeBytes)
        assertEquals(50L, leech.transferredBytes)

        val seed = projected.getValue("t2")
        assertEquals(P2pMode.SEED, seed.mode)
        assertEquals(100, seed.progress)
        assertEquals(P2pStatus.DONE, seed.status)
        assertEquals("dev-y", seed.peerName)        // falls back to peer id when unknown

        assertEquals(P2pStatus.FAILED, projected.getValue("t3").status)
    }
}
