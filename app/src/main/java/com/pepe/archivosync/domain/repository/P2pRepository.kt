package com.pepe.archivosync.domain.repository

import com.pepe.archivosync.domain.model.FileNode
import com.pepe.archivosync.domain.model.P2pTransfer
import kotlinx.coroutines.flow.Flow

/**
 * Dashboard view over the real WebRTC P2P channel. Transfers are projected from
 * the live DataChannel activity managed by [P2pConnectivityRepository]; seeding
 * pushes files to every currently-linked peer.
 */
interface P2pRepository {
    fun observeTransfers(): Flow<List<P2pTransfer>>

    /** Sends the given files to every currently-linked peer over WebRTC. */
    suspend fun seed(files: List<FileNode>)
}
