package io.github.diegog0477.zombiebox.shared

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Collections
import java.util.Timer
import java.util.TimerTask
import org.json.JSONObject

/** HTTP/1.1 transport; provider credentials are never persisted by the client. */
class GatewayApi {
    private data class Profile(val base: String, val device: String, val token: String)

    @Volatile private var profile = Profile("", "", "")
    @Volatile private var closed = false
    var base: String
        get() = profile.base
        set(value) {
            profile = profile.copy(base = value)
        }

    var device: String
        get() = profile.device
        set(value) {
            profile = profile.copy(device = value)
        }

    var token: String
        get() = profile.token
        set(value) {
            profile = profile.copy(token = value)
        }

    fun configure(base: String, device: String, token: String) {
        profile = Profile(base, device, token)
    }

    private val uploads = Collections.synchronizedSet(HashSet<InputStream>())
    private val active = Collections.synchronizedSet(HashSet<HttpURLConnection>())

    fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        admin: String = "",
    ): JSONObject = JSONObject(String(bytes(method, path, body, admin), Charsets.UTF_8))

    fun frame(path: String): ByteArray = bytes("GET", path, null, "")

    /** Timed, streaming sample. No 1-MiB allocation, redirects or compression. */
    fun downloadSample(): DownloadSample {
        if (closed) throw GatewayFailure(503)
        val settings = profile
        val endpoint = URL(settings.base + "/v1/network/sample")
        require(endpoint.protocol in listOf("http", "https") && endpoint.userInfo == null)
        val http = endpoint.openConnection() as HttpURLConnection
        val started = System.nanoTime()
        var received = 0
        active.add(http)
        try {
            if (closed) throw GatewayFailure(503)
            http.connectTimeout = 1500
            http.readTimeout = 1500
            http.instanceFollowRedirects = false
            http.useCaches = false
            http.setRequestProperty("Accept-Encoding", "identity")
            http.setRequestProperty("Authorization", "Bearer ${settings.token}")
            http.setRequestProperty("X-Zombie-Device", settings.device)
            if (http.responseCode != 200 || http.contentLength != 1048576) throw GatewayFailure(502)
            val id = http.getHeaderField("X-Zombie-Sample") ?: throw GatewayFailure(502)
            if (!id.matches(Regex("[0-9a-f]{32}"))) throw GatewayFailure(502)
            http.inputStream.use { input ->
                val buffer = ByteArray(8192)
                try {
                    while (received < 1048576) {
                        val remaining = 3000 - (System.nanoTime() - started) / 1000000
                        if (remaining <= 0) break
                        http.readTimeout = remaining.coerceAtMost(1500).toInt()
                        val count = input.read(buffer, 0, minOf(buffer.size, 1048576 - received))
                        if (count < 0) throw GatewayFailure(502)
                        received += count
                    }
                } catch (_: SocketTimeoutException) {
                    // A time-bounded partial sample measures delivered bytes, not decoder health.
                }
            }
            if (received < 32768 || closed || profile != settings) throw GatewayFailure(503)
            val elapsed = ((System.nanoTime() - started) / 1000000).coerceAtLeast(1)
            if (elapsed > 4500) throw GatewayFailure(503)
            return DownloadSample(id, received, elapsed)
        } finally {
            active.remove(http)
            http.disconnect()
        }
    }

    private fun bytes(method: String, path: String, body: JSONObject?, admin: String): ByteArray {
        if (closed) throw GatewayFailure(503)
        val settings = profile
        val endpoint = URL(settings.base + path)
        require(endpoint.protocol == "http" || endpoint.protocol == "https")
        require(endpoint.userInfo == null && endpoint.host.isNotEmpty())
        val http = endpoint.openConnection() as HttpURLConnection
        active.add(http)
        try {
            if (closed) throw GatewayFailure(503)
            http.requestMethod = method
            http.connectTimeout =
                if (path == "/v1/device/network" || path == "/v1/companion/proof") 1500 else 5000
            http.readTimeout =
                if (path == "/v1/device/network" || path == "/v1/companion/proof") 1500
                else if (path == "/v1/playback" || path.startsWith("/v1/companion/media/")) 30000
                else if (path.startsWith("/v1/events")) 25000
                else if (path == "/v1/youtube/receiver") 25000
                else if (path == "/v1/browser") 20000
                else if (
                    path.startsWith("/v1/playback/") &&
                        (path.contains("/subtitles/") || path.endsWith("/quality"))
                )
                    45000
                else 12000
            http.instanceFollowRedirects = false
            http.useCaches = false
            http.setRequestProperty("Accept", "application/json")
            if (settings.token.isNotEmpty()) {
                http.setRequestProperty("Authorization", "Bearer ${settings.token}")
                http.setRequestProperty("X-Zombie-Device", settings.device)
            }
            if (admin.isNotEmpty()) http.setRequestProperty("X-Zombie-Admin-Code", admin)
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                http.doOutput = true
                http.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                http.setFixedLengthStreamingMode(bytes.size)
                http.outputStream.use { it.write(bytes) }
            }
            val status = http.responseCode
            if (status !in 200..299) throw GatewayFailure(status)
            val result = ByteArrayOutputStream()
            http.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (result.size() + count > 1024 * 1024) throw GatewayFailure(413)
                    result.write(buffer, 0, count)
                }
            }
            return result.toByteArray()
        } finally {
            active.remove(http)
            http.disconnect()
        }
    }

    /** Fixed-length, bounded upload; no redirects, whole-file buffering or phone server. */
    fun upload(path: String, size: Int, input: InputStream, progress: (Int) -> Unit): JSONObject {
        require(path.matches(Regex("/v1/companion/media/[0-9a-f]{32}")))
        require(size in 1..268435456)
        if (closed) throw GatewayFailure(503)
        val settings = profile
        val endpoint = URL(settings.base + path)
        require(endpoint.protocol in listOf("http", "https") && endpoint.userInfo == null)
        val http = endpoint.openConnection() as HttpURLConnection
        val timer = Timer("media-upload-deadline", true)
        active.add(http)
        uploads.add(input)
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    http.disconnect()
                    try {
                        input.close()
                    } catch (_: Exception) {}
                }
            },
            300000,
        )
        try {
            if (closed) throw GatewayFailure(503)
            http.requestMethod = "PUT"
            http.connectTimeout = 5000
            http.readTimeout = 15000
            http.instanceFollowRedirects = false
            http.useCaches = false
            http.doOutput = true
            http.setRequestProperty("Authorization", "Bearer ${settings.token}")
            http.setRequestProperty("X-Zombie-Device", settings.device)
            http.setRequestProperty("Content-Type", "application/octet-stream")
            http.setFixedLengthStreamingMode(size)
            http.outputStream.use { output ->
                val buffer = ByteArray(32768)
                var sent = 0
                var reported = -1
                while (sent < size) {
                    if (closed || settings != profile) throw GatewayFailure(503)
                    val count = input.read(buffer, 0, minOf(buffer.size, size - sent))
                    if (count < 0) throw GatewayFailure(400)
                    if (count == 0) continue
                    output.write(buffer, 0, count)
                    sent += count
                    val percent = (sent.toLong() * 100 / size).toInt()
                    if (percent != reported) {
                        reported = percent
                        progress(percent)
                    }
                }
                if (input.read() != -1) throw GatewayFailure(413)
            }
            if (http.responseCode != 201) throw GatewayFailure(http.responseCode)
            val result = ByteArrayOutputStream()
            http.inputStream.use { response ->
                val buffer = ByteArray(1024)
                while (true) {
                    val n = response.read(buffer)
                    if (n < 0) break
                    if (result.size() + n > 16384) throw GatewayFailure(502)
                    result.write(buffer, 0, n)
                }
            }
            if (closed) throw GatewayFailure(503)
            return JSONObject(String(result.toByteArray(), Charsets.UTF_8))
        } finally {
            timer.cancel()
            uploads.remove(input)
            active.remove(http)
            http.disconnect()
        }
    }

    fun close() {
        closed = true
        disconnect()
    }

    fun disconnect() {
        val snapshot = synchronized(active) { active.toList() }
        for (connection in snapshot) connection.disconnect()
        val streams = synchronized(uploads) { uploads.toList() }
        for (input in streams) try {
            input.close()
        } catch (_: Exception) {}
    }
}

class GatewayFailure(val status: Int) : Exception("Gateway request failed")

data class DownloadSample(val id: String, val bytes: Int, val elapsedMs: Long)
