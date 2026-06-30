package com.pepe.archivosync.domain.model

enum class P2pMode { SEED, LEECH }
enum class P2pStatus { ACTIVE, PAUSED }

/**
 * A BitTorrent-style transfer handled by the P2P channel. Speeds are in KB/s.
 */
data class P2pTransfer(
    val id: String,
    val name: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val mode: P2pMode,
    val progress: Int,            // 0..100
    val ratio: Double,
    val upRateKbps: Long,
    val downRateKbps: Long,
    val seeds: Int,
    val peers: Int,
    val status: P2pStatus,
    val infoHash: String,
) {
    val magnetUri: String get() = "magnet:?xt=urn:btih:$infoHash"
}
