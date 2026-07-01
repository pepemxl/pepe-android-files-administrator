package com.pepe.archivosync.data.webrtc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the signaling wire protocol (docs/referencia-api.md §C).
 * Exercises [SignalCodec] directly — no socket/IO involved.
 */
class SignalCodecTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val codec = SignalCodec(json)

    // --- decode: welcome ---------------------------------------------------

    @Test
    fun `decodes welcome with top-level fields (real server shape)`() {
        // Matches OrchestratorServer::onOpen: device_id + iceServers at top level.
        val frame = """
            {"type":"welcome","device_id":"dev-1","iceServers":[
              {"urls":["stun:stun.example.com:3478"]},
              {"urls":["turn:turn.example.com:3478"],"username":"u","credential":"c","credentialType":"password"}
            ]}
        """.trimIndent()

        val event = codec.decode(frame) as SignalEvent.Welcome
        assertEquals("dev-1", event.deviceId)
        assertEquals(2, event.iceServers.size)
        assertEquals(listOf("stun:stun.example.com:3478"), event.iceServers[0].urls)
        assertNull(event.iceServers[0].username)
        assertEquals("u", event.iceServers[1].username)
        assertEquals("c", event.iceServers[1].credential)
    }

    @Test
    fun `decodes welcome with data-wrapped fields (lenient fallback)`() {
        val frame = """{"type":"welcome","data":{"device_id":"dev-2","iceServers":[{"urls":["stun:a:3478"]}]}}"""
        val event = codec.decode(frame) as SignalEvent.Welcome
        assertEquals("dev-2", event.deviceId)
        assertEquals(listOf("stun:a:3478"), event.iceServers.single().urls)
    }

    @Test
    fun `decodes welcome with urls as bare string`() {
        val frame = """{"type":"welcome","device_id":"d","iceServers":[{"urls":"stun:a:3478"}]}"""
        val event = codec.decode(frame) as SignalEvent.Welcome
        assertEquals(listOf("stun:a:3478"), event.iceServers.single().urls)
    }

    @Test
    fun `decodes welcome without ice servers`() {
        val event = codec.decode("""{"type":"welcome","device_id":"d"}""") as SignalEvent.Welcome
        assertEquals("d", event.deviceId)
        assertTrue(event.iceServers.isEmpty())
    }

    // --- decode: offer / answer / ice / bye / error ------------------------

    @Test
    fun `decodes offer with from and sdp`() {
        val frame = """{"type":"offer","from":"peer-9","data":{"type":"offer","sdp":"v=0..."}}"""
        val event = codec.decode(frame) as SignalEvent.Offer
        assertEquals("peer-9", event.from)
        assertEquals("v=0...", event.sdp)
    }

    @Test
    fun `decodes answer with from and sdp`() {
        val frame = """{"type":"answer","from":"peer-2","data":{"type":"answer","sdp":"a=1"}}"""
        val event = codec.decode(frame) as SignalEvent.Answer
        assertEquals("peer-2", event.from)
        assertEquals("a=1", event.sdp)
    }

    @Test
    fun `decodes ice candidate`() {
        val frame = """{"type":"ice","from":"p","data":{"candidate":"candidate:1 udp","sdpMid":"0","sdpMLineIndex":2}}"""
        val event = codec.decode(frame) as SignalEvent.Ice
        assertEquals("p", event.from)
        assertEquals("candidate:1 udp", event.candidate)
        assertEquals("0", event.sdpMid)
        assertEquals(2, event.sdpMLineIndex)
    }

    @Test
    fun `decodes ice with null sdpMid and defaults mline index`() {
        val frame = """{"type":"ice","from":"p","data":{"candidate":"c","sdpMid":null}}"""
        val event = codec.decode(frame) as SignalEvent.Ice
        assertNull(event.sdpMid)
        assertEquals(0, event.sdpMLineIndex)
    }

    @Test
    fun `decodes bye`() {
        val event = codec.decode("""{"type":"bye","from":"peer-7"}""") as SignalEvent.Bye
        assertEquals("peer-7", event.from)
    }

    @Test
    fun `decodes error with top-level message (real server shape)`() {
        // Matches SignalingHandler::send: {"type":"error","message":...} top level.
        val event = codec.decode("""{"type":"error","message":"unauthorized"}""") as SignalEvent.Failed
        assertEquals("unauthorized", event.message)
    }

    @Test
    fun `decodes error with data-wrapped message (lenient fallback)`() {
        val event = codec.decode("""{"type":"error","data":{"message":"nope"}}""") as SignalEvent.Failed
        assertEquals("nope", event.message)
    }

    // --- decode: malformed / unknown --------------------------------------

    @Test
    fun `unknown type decodes to null`() {
        assertNull(codec.decode("""{"type":"totally-unknown"}"""))
    }

    @Test
    fun `missing type decodes to null`() {
        assertNull(codec.decode("""{"from":"x"}"""))
    }

    @Test
    fun `malformed json decodes to null`() {
        assertNull(codec.decode("not json at all"))
        assertNull(codec.decode(""))
        assertNull(codec.decode("[1,2,3]"))
    }

    @Test
    fun `missing sdp defaults to empty string`() {
        val event = codec.decode("""{"type":"offer","from":"p","data":{}}""") as SignalEvent.Offer
        assertEquals("", event.sdp)
    }

    // --- encode ------------------------------------------------------------

    @Test
    fun `encodes offer with envelope and nested sdp`() {
        val obj = json.parseToJsonElement(codec.encodeOffer("peer-1", "v=0")).jsonObject
        assertEquals("offer", obj["type"]?.jsonPrimitive?.content)
        assertEquals("peer-1", obj["to"]?.jsonPrimitive?.content)
        val data = obj["data"]!!.jsonObject
        assertEquals("offer", data["type"]?.jsonPrimitive?.content)
        assertEquals("v=0", data["sdp"]?.jsonPrimitive?.content)
    }

    @Test
    fun `encodes answer round-trips through decode`() {
        val decoded = codec.decode(codec.encodeAnswer("peer-5", "a=xyz")) as SignalEvent.Answer
        assertEquals("a=xyz", decoded.sdp)
    }

    @Test
    fun `encodes ice round-trips through decode`() {
        val decoded = codec.decode(codec.encodeIce("peer-3", "cand", "1", 4)) as SignalEvent.Ice
        assertEquals("cand", decoded.candidate)
        assertEquals("1", decoded.sdpMid)
        assertEquals(4, decoded.sdpMLineIndex)
    }

    @Test
    fun `encodes ice with null sdpMid round-trips to null`() {
        val decoded = codec.decode(codec.encodeIce("peer-3", "cand", null, 0)) as SignalEvent.Ice
        assertNull(decoded.sdpMid)
    }

    @Test
    fun `encodes bye envelope`() {
        val obj = json.parseToJsonElement(codec.encodeBye("peer-8")).jsonObject
        assertEquals("bye", obj["type"]?.jsonPrimitive?.content)
        assertEquals("peer-8", obj["to"]?.jsonPrimitive?.content)
    }
}
