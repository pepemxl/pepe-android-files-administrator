package com.pepe.archivosync.domain.repository

import com.pepe.archivosync.domain.model.FileNode
import com.pepe.archivosync.domain.model.P2pTransfer
import kotlinx.coroutines.flow.Flow

/**
 * Orchestrates BitTorrent-style transfers. The concrete client talks to the
 * external P2P orchestrator (pepe-p2p-orquestrator); here it is modelled as a
 * reactive store of [P2pTransfer]s plus seed/pause controls.
 */
interface P2pRepository {
    fun observeTransfers(): Flow<List<P2pTransfer>>

    /** Begin seeding the given source files; returns the new transfers. */
    suspend fun seed(files: List<FileNode>)

    suspend fun setPaused(id: String, paused: Boolean)
}
