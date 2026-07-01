package com.pepe.archivosync.data.webrtc

import com.pepe.archivosync.domain.model.IceServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure (de)serialization of the orchestrator signaling protocol
 * (docs/referencia-api.md §C). Kept free of any socket/IO so the framing is
 * unit-testable in isolation; [SignalingClient] owns the transport and delegates
 * every frame to this codec.
 */
@Singleton
class SignalCodec @Inject constructor(private val json: Json) {

    /** Parses one inbound frame; returns null for unknown/malformed frames. */
    fun decode(text: String): SignalEvent? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val type = obj["type"]?.jsonPrimitive?.content ?: return null
        val from = obj["from"]?.jsonPrimitive?.content ?: ""
        val data = obj["data"]?.jsonObject
        // `welcome`/`error` carry their fields at the top level (see the
        // orchestrator's OrchestratorServer/SignalingHandler); relayed frames
        // (offer/answer/ice) carry them under `data`. We look under `data` first
        // and fall back to the top level so both shapes decode.
        fun field(key: String) = data?.get(key) ?: obj[key]
        return when (type) {
            "welcome" -> SignalEvent.Welcome(
                deviceId = field("device_id")?.jsonPrimitive?.contentOrNull() ?: "",
                iceServers = field("iceServers")?.let(::parseIceServers) ?: emptyList(),
            )
            "offer" -> SignalEvent.Offer(from, field("sdp")?.jsonPrimitive?.contentOrNull() ?: "")
            "answer" -> SignalEvent.Answer(from, field("sdp")?.jsonPrimitive?.contentOrNull() ?: "")
            "ice" -> SignalEvent.Ice(
                from = from,
                candidate = field("candidate")?.jsonPrimitive?.contentOrNull() ?: "",
                sdpMid = field("sdpMid")?.jsonPrimitive?.contentOrNull(),
                sdpMLineIndex = field("sdpMLineIndex")?.jsonPrimitive?.int ?: 0,
            )
            "bye" -> SignalEvent.Bye(from)
            "error" -> SignalEvent.Failed(field("message")?.jsonPrimitive?.contentOrNull() ?: "error")
            else -> null
        }
    }

    fun encodeOffer(to: String, sdp: String): String = encode(buildJsonObject {
        put("type", "offer"); put("to", to)
        putJsonObject("data") { put("type", "offer"); put("sdp", sdp) }
    })

    fun encodeAnswer(to: String, sdp: String): String = encode(buildJsonObject {
        put("type", "answer"); put("to", to)
        putJsonObject("data") { put("type", "answer"); put("sdp", sdp) }
    })

    fun encodeIce(to: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int): String = encode(buildJsonObject {
        put("type", "ice"); put("to", to)
        putJsonObject("data") {
            put("candidate", candidate)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
        }
    })

    fun encodeBye(to: String): String = encode(buildJsonObject {
        put("type", "bye"); put("to", to)
    })

    private fun encode(obj: JsonObject): String = json.encodeToString(JsonObject.serializer(), obj)

    /** `urls` may arrive as an array or a bare string; accept both. */
    private fun parseIceServers(element: kotlinx.serialization.json.JsonElement): List<IceServer> =
        (element as? JsonArray)?.map { el ->
            val o = el.jsonObject
            val urls = when (val u = o["urls"]) {
                is JsonArray -> u.map { it.jsonPrimitive.content }
                is JsonPrimitive -> listOf(u.content)
                else -> emptyList()
            }
            IceServer(
                urls = urls,
                username = o["username"]?.jsonPrimitive?.contentOrNull(),
                credential = o["credential"]?.jsonPrimitive?.contentOrNull(),
            )
        } ?: emptyList()
}

/** Like `JsonPrimitive.contentOrNull` but tolerant of the JSON `null` literal. */
private fun JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
