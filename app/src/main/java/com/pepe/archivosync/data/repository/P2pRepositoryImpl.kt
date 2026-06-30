package com.pepe.archivosync.data.repository

import com.pepe.archivosync.domain.model.FileKind
import com.pepe.archivosync.domain.model.FileNode
import com.pepe.archivosync.domain.model.P2pMode
import com.pepe.archivosync.domain.model.P2pStatus
import com.pepe.archivosync.domain.model.P2pTransfer
import com.pepe.archivosync.domain.repository.P2pRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * In-process model of the P2P channel. A real build delegates piece exchange to
 * the external orchestrator (pepe-p2p-orquestrator); here a ticker advances
 * leech progress and ratios so the UI behaves like a live swarm.
 */
@Singleton
class P2pRepositoryImpl @Inject constructor(
    scope: CoroutineScope,
) : P2pRepository {

    private val state = MutableStateFlow(seedData())

    init {
        scope.launch {
            while (true) {
                delay(700)
                tick()
            }
        }
    }

    override fun observeTransfers(): Flow<List<P2pTransfer>> = state.asStateFlow()

    override suspend fun seed(files: List<FileNode>) {
        if (files.isEmpty()) return
        val news = files.map { node ->
            P2pTransfer(
                id = "s${System.nanoTime()}_${node.name.hashCode()}",
                name = node.name,
                kind = node.kind,
                sizeBytes = node.sizeBytes,
                mode = P2pMode.SEED,
                progress = 100,
                ratio = 0.0,
                upRateKbps = Random.nextLong(200, 800),
                downRateKbps = 0,
                seeds = 0,
                peers = Random.nextInt(0, 20),
                status = P2pStatus.ACTIVE,
                infoHash = randomHash(),
            )
        }
        state.update { news + it }
    }

    override suspend fun setPaused(id: String, paused: Boolean) {
        state.update { list ->
            list.map {
                if (it.id == id) it.copy(status = if (paused) P2pStatus.PAUSED else P2pStatus.ACTIVE)
                else it
            }
        }
    }

    private fun tick() {
        state.update { list ->
            list.map { p ->
                if (p.status != P2pStatus.ACTIVE) return@map p
                when (p.mode) {
                    P2pMode.LEECH -> {
                        val np = (p.progress + Random.nextInt(2, 9)).coerceAtMost(100)
                        if (np >= 100) p.copy(
                            progress = 100,
                            mode = P2pMode.SEED,
                            downRateKbps = 0,
                            upRateKbps = Random.nextLong(300, 1200),
                            ratio = p.ratio + 0.01,
                        ) else p.copy(
                            progress = np,
                            downRateKbps = Random.nextLong(2000, 7000),
                            upRateKbps = Random.nextLong(40, 160),
                            ratio = p.ratio + 0.002,
                        )
                    }
                    P2pMode.SEED -> p.copy(
                        upRateKbps = Random.nextLong(200, 1600),
                        ratio = p.ratio + 0.01,
                        peers = (p.peers + if (Random.nextBoolean()) 1 else -1).coerceAtLeast(0),
                    )
                }
            }
        }
    }

    private fun randomHash(): String =
        (0 until 16).joinToString("") { Random.nextInt(0, 16).toString(16) }

    private fun seedData() = listOf(
        P2pTransfer("p1", "ubuntu-26.04-desktop.iso", FileKind.BIN, 4_100_000_000L, P2pMode.SEED, 100, 2.34, 1240, 0, 0, 38, P2pStatus.ACTIVE, "a3f9c1d8e2b47f06"),
        P2pTransfer("p2", "dataset_full.csv", FileKind.CSV, 56_000_000, P2pMode.LEECH, 64, 0.12, 120, 3400, 12, 5, P2pStatus.ACTIVE, "7c1e93a0fd5b2284"),
        P2pTransfer("p3", "backup_full_2026-06-28.zip", FileKind.ZIP, 820_000_000, P2pMode.SEED, 100, 0.88, 540, 0, 0, 14, P2pStatus.ACTIVE, "9b22ef4471ca8d13"),
        P2pTransfer("p4", "fotos_2025.zip", FileKind.ZIP, 1_400_000_000, P2pMode.LEECH, 23, 0.04, 60, 5600, 8, 3, P2pStatus.PAUSED, "2d8a6f019e3c7b55"),
    )
}
