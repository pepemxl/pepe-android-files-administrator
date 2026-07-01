package com.pepe.archivosync.data.destination.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class SigV4Test {

    // Official AWS SigV4 test suite: `get-vanilla`.
    @Test
    fun `get-vanilla matches AWS vector`() {
        val auth = SigV4.authorization(
            method = "GET",
            canonicalUri = "/",
            canonicalQuery = "",
            headers = listOf(
                "host" to "example.amazonaws.com",
                "x-amz-date" to "20150830T123600Z",
            ),
            payloadHash = SigV4.EMPTY_SHA256,
            accessKey = "AKIDEXAMPLE",
            secretKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
            region = "us-east-1",
            service = "service",
            amzDate = "20150830T123600Z",
            dateStamp = "20150830",
        )
        assertEquals(
            "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, " +
                "SignedHeaders=host;x-amz-date, " +
                "Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31",
            auth,
        )
    }

    @Test
    fun `uri encode keeps unreserved and slash`() {
        assertEquals("a%20b/c%2Bd", SigV4.uriEncode("a b/c+d", encodeSlash = false))
        assertEquals("a%2Fb", SigV4.uriEncode("a/b", encodeSlash = true))
    }
}
