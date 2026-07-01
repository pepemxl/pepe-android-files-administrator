package com.pepe.archivosync.data.destination.cloud

import com.pepe.archivosync.data.remote.ProgressRequestBody
import com.pepe.archivosync.domain.model.AppSettings
import com.pepe.archivosync.domain.model.DownloadItem
import com.pepe.archivosync.domain.model.DownloadStatus
import com.pepe.archivosync.domain.model.FileKind
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.URLDecoder

/** WebDAV backend (Nextcloud / ownCloud / generic DAV) over plain HTTP. */
class WebDavClient(private val client: OkHttpClient) {

    private fun auth(s: AppSettings) = Credentials.basic(s.accessKey, s.secretKey)
    private fun base(s: AppSettings) = s.host.trim().trimEnd('/')

    private fun encPath(p: String): String =
        p.split('/').filter { it.isNotEmpty() }.joinToString("/") { SigV4.uriEncode(it, true) }

    private fun dirUrl(s: AppSettings): String {
        val sub = encPath(s.cloudPath.trim().trim('/'))
        return if (sub.isEmpty()) base(s) else "${base(s)}/$sub"
    }

    fun test(s: AppSettings): Boolean {
        val req = Request.Builder().url(dirUrl(s))
            .header("Authorization", auth(s)).header("Depth", "0")
            .method("PROPFIND", null).build()
        client.newCall(req).execute().use { return it.isSuccessful }
    }

    fun list(s: AppSettings): List<DownloadItem> {
        val body = PROPFIND_BODY.toRequestBody("application/xml".toMediaTypeOrNull())
        val req = Request.Builder().url(dirUrl(s))
            .header("Authorization", auth(s)).header("Depth", "1")
            .method("PROPFIND", body).build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            return parseMultistatus(resp.body!!.byteStream())
        }
    }

    fun upload(s: AppSettings, fileName: String, sizeBytes: Long, input: InputStream, onProgress: (Long) -> Unit): String {
        val remote = CloudIo.joinRemote(s.cloudPath.trim().trim('/'), fileName)
        val url = "${base(s)}/${encPath(remote)}"
        val body = ProgressRequestBody(input, sizeBytes, "application/octet-stream".toMediaTypeOrNull(), onProgress)
        val req = Request.Builder().url(url).header("Authorization", auth(s)).put(body).build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            return url
        }
    }

    fun download(s: AppSettings, item: DownloadItem, sink: OutputStream, onProgress: (Long) -> Unit) {
        val href = item.remotePath ?: item.id
        val url = if (href.startsWith("http://") || href.startsWith("https://")) {
            href
        } else {
            val u = URI(base(s))
            val origin = "${u.scheme}://${u.authority}"
            origin + href
        }
        val req = Request.Builder().url(url).header("Authorization", auth(s)).get().build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            CloudIo.copy(resp.body!!.byteStream(), sink, onProgress)
        }
    }

    private fun parseMultistatus(stream: InputStream): List<DownloadItem> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(stream, "UTF-8")
        val out = mutableListOf<DownloadItem>()
        var href: String? = null
        var size = 0L
        var isDir = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "href" -> href = parser.nextText()
                    "getcontentlength" -> size = parser.nextText().trim().toLongOrNull() ?: 0L
                    "collection" -> isDir = true
                }
                XmlPullParser.END_TAG -> if (parser.name == "response") {
                    val h = href
                    if (h != null && h.isNotBlank() && !isDir) {
                        val decoded = URLDecoder.decode(h.trimEnd('/'), "UTF-8")
                        val name = decoded.substringAfterLast('/')
                        out += DownloadItem(h, name, FileKind.fromName(name), size, DownloadStatus.AVAILABLE, 0, remotePath = h)
                    }
                    href = null; size = 0L; isDir = false
                }
            }
            event = parser.next()
        }
        return out
    }

    private companion object {
        const val PROPFIND_BODY =
            """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:getcontentlength/><d:resourcetype/></d:prop></d:propfind>"""
    }
}
