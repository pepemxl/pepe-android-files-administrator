package com.pepe.archivosync.data.destination.cloud

import com.pepe.archivosync.data.remote.ProgressRequestBody
import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.FileKind
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * S3 REST backend (also Google Cloud Storage via its S3-interoperability
 * endpoint). Manual SigV4 ([SigV4]); streams uploads with `UNSIGNED-PAYLOAD`.
 */
class S3Client(private val client: OkHttpClient) {

    private data class Target(val host: String, val basePath: String)

    private fun target(s: AppSettings): Target {
        val bucket = s.host.trim().trim('/')
        return if (s.cloudProvider.name.equals("GCS", true)) {
            Target("storage.googleapis.com", "/" + SigV4.uriEncode(bucket, false))
        } else {
            Target("$bucket.s3.${s.region.trim()}.amazonaws.com", "")
        }
    }

    private fun dates(): Pair<String, String> {
        val now = Date()
        val amz = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return amz.format(now) to day.format(now)
    }

    private fun canonQuery(pairs: List<Pair<String, String>>): String =
        pairs.map { SigV4.uriEncode(it.first, true) to SigV4.uriEncode(it.second, true) }
            .sortedBy { it.first }
            .joinToString("&") { "${it.first}=${it.second}" }

    private fun signedGet(url: String, host: String, canonicalUri: String, canonicalQuery: String, s: AppSettings): okhttp3.Response {
        val (amzDate, dateStamp) = dates()
        val auth = SigV4.authorization(
            "GET", canonicalUri, canonicalQuery,
            listOf("host" to host, "x-amz-content-sha256" to SigV4.EMPTY_SHA256, "x-amz-date" to amzDate),
            SigV4.EMPTY_SHA256, s.accessKey, s.secretKey, s.region, "s3", amzDate, dateStamp,
        )
        val req = Request.Builder().url(url)
            .header("x-amz-content-sha256", SigV4.EMPTY_SHA256)
            .header("x-amz-date", amzDate)
            .header("Authorization", auth)
            .get().build()
        return client.newCall(req).execute()
    }

    fun test(s: AppSettings): Boolean {
        val t = target(s)
        val q = canonQuery(listOf("list-type" to "2", "max-keys" to "1"))
        val listPath = t.basePath.ifEmpty { "/" }
        signedGet("https://${t.host}$listPath?$q", t.host, listPath, q, s).use { return it.isSuccessful }
    }

    fun list(s: AppSettings): List<DownloadItem> {
        val t = target(s)
        val prefix = s.cloudPath.trim().trim('/')
        val pairs = buildList {
            add("list-type" to "2")
            if (prefix.isNotEmpty()) add("prefix" to "$prefix/")
        }
        val q = canonQuery(pairs)
        val listPath = t.basePath.ifEmpty { "/" }
        signedGet("https://${t.host}$listPath?$q", t.host, listPath, q, s).use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            return parseListXml(resp.body!!.byteStream())
        }
    }

    fun upload(s: AppSettings, fileName: String, sizeBytes: Long, input: InputStream, onProgress: (Long) -> Unit): String {
        val t = target(s)
        val key = CloudIo.joinRemote(s.cloudPath, fileName)
        val canonicalUri = "${t.basePath}/${SigV4.uriEncode(key, false)}"
        val url = "https://${t.host}$canonicalUri"
        val (amzDate, dateStamp) = dates()
        val auth = SigV4.authorization(
            "PUT", canonicalUri, "",
            listOf("host" to t.host, "x-amz-content-sha256" to SigV4.UNSIGNED_PAYLOAD, "x-amz-date" to amzDate),
            SigV4.UNSIGNED_PAYLOAD, s.accessKey, s.secretKey, s.region, "s3", amzDate, dateStamp,
        )
        val body = ProgressRequestBody(input, sizeBytes, "application/octet-stream".toMediaTypeOrNull(), onProgress)
        val req = Request.Builder().url(url)
            .header("x-amz-content-sha256", SigV4.UNSIGNED_PAYLOAD)
            .header("x-amz-date", amzDate)
            .header("Authorization", auth)
            .put(body).build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            return url
        }
    }

    fun download(s: AppSettings, item: DownloadItem, sink: OutputStream, onProgress: (Long) -> Unit) {
        val t = target(s)
        val key = (item.remotePath ?: item.id).trimStart('/')
        val canonicalUri = "${t.basePath}/${SigV4.uriEncode(key, false)}"
        signedGet("https://${t.host}$canonicalUri", t.host, canonicalUri, "", s).use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            CloudIo.copy(resp.body!!.byteStream(), sink, onProgress)
        }
    }

    private fun parseListXml(stream: InputStream): List<DownloadItem> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, "UTF-8")
        val out = mutableListOf<DownloadItem>()
        var key: String? = null
        var size = 0L
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "Key" -> key = parser.nextText()
                    "Size" -> size = parser.nextText().trim().toLongOrNull() ?: 0L
                }
                XmlPullParser.END_TAG -> if (parser.name == "Contents") {
                    val k = key
                    if (k != null && !k.endsWith("/")) {
                        val name = k.substringAfterLast('/')
                        out += DownloadItem(k, name, FileKind.fromName(name), size, DownloadStatus.AVAILABLE, 0, remotePath = k)
                    }
                    key = null; size = 0L
                }
            }
            event = parser.next()
        }
        return out
    }
}
