package com.pepe.archivosync.domain.usecase

import com.pepe.archivosync.domain.model.FileNode
import com.pepe.archivosync.domain.repository.P2pRepository
import javax.inject.Inject

/** Starts seeding a selection of files over the P2P channel. */
class SeedSelectionUseCase @Inject constructor(
    private val p2p: P2pRepository,
) {
    suspend operator fun invoke(files: List<FileNode>) {
        p2p.seed(files.filter { !it.isDirectory })
    }
}
