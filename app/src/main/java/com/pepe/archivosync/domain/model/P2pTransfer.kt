package com.pepe.archivosync.domain.model

enum class P2pMode { SEED, LEECH }
enum class P2pStatus { ACTIVE, DONE, FAILED }

/**
 * A file moving over the real WebRTC P2P channel, shaped for the dashboard.
 * SEED = a file this device is sending to a peer; LEECH = one it is receiving.
 * Derived from [P2pFileTransfer] (rates are sampled per second). Speeds in KB/s.
 */
data class P2pTransfer(
    val id: String,
    val name: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val transferredBytes: Long,
    val mode: P2pMode,
    val progress: Int,            // 0..100
    val upRateKbps: Long,
    val downRateKbps: Long,
    val peerName: String,
    val status: P2pStatus,
)
