package com.pepe.archivosync.data.destination.cloud

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal AWS Signature V4 signer for the S3 REST API (also used for Google
 * Cloud Storage via its S3-interoperability endpoint). Pure crypto, unit-tested
 * against the official AWS `get-vanilla` test vector.
 */
object SigV4 {

    const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"

    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).toHex()

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    /** RFC 3986 percent-encoding; keeps `/` when [encodeSlash] is false. */
    fun uriEncode(input: String, encodeSlash: Boolean): String {
        val out = StringBuilder(input.length)
        for (b in input.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            when {
                c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
                    c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code -> out.append(c.toChar())
                c == '/'.code && !encodeSlash -> out.append('/')
                else -> out.append('%').append("%02X".format(c))
            }
        }
        return out.toString()
    }

    /** Builds the `Authorization` header value for one SigV4-signed request. */
    fun authorization(
        method: String,
        canonicalUri: String,
        canonicalQuery: String,
        headers: List<Pair<String, String>>,
        payloadHash: String,
        accessKey: String,
        secretKey: String,
        region: String,
        service: String,
        amzDate: String,
        dateStamp: String,
    ): String {
        val hs = headers
            .map { it.first.lowercase() to it.second.trim() }
            .sortedBy { it.first }
        val canonicalHeaders = hs.joinToString("") { "${it.first}:${it.second}\n" }
        val signedHeaders = hs.joinToString(";") { it.first }

        val canonicalRequest =
            "$method\n$canonicalUri\n$canonicalQuery\n$canonicalHeaders\n$signedHeaders\n$payloadHash"
        val scope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign =
            "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))}"

        val kDate = hmac("AWS4$secretKey".toByteArray(Charsets.UTF_8), dateStamp.toByteArray())
        val kRegion = hmac(kDate, region.toByteArray())
        val kService = hmac(kRegion, service.toByteArray())
        val kSigning = hmac(kService, "aws4_request".toByteArray())
        val signature = hmac(kSigning, stringToSign.toByteArray(Charsets.UTF_8)).toHex()

        return "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"
    }
}
