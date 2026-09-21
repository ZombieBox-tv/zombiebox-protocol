package io.github.diegog0477.zombiebox.shared

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/** HTTP/1.1 transport; provider credentials are never persisted by the client. */
class GatewayApi {
    private data class Profile(val base: String, val device: String, val token: String)
    @Volatile private var profile = Profile("", "", "")
    @Volatile private var closed = false
    var base: String
        get() = profile.base
        set(value) { profile = profile.copy(base = value) }
    var device: String
        get() = profile.device
        set(value) { profile = profile.copy(device = value) }
    var token: String
        get() = profile.token
        set(value) { profile = profile.copy(token = value) }
    fun configure(base: String, device: String, token: String) { profile = Profile(base, device, token) }
    private val active = Collections.synchronizedSet(HashSet<HttpURLConnection>())

    fun request(method: String, path: String, body: JSONObject? = null, admin: String = ""): JSONObject =
        JSONObject(String(bytes(method, path, body, admin), Charsets.UTF_8))

    fun frame(path: String): ByteArray = bytes("GET", path, null, "")

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
            http.connectTimeout = 5000
            http.readTimeout = if (path.startsWith("/v1/events")) 25000 else if (path == "/v1/youtube/receiver") 25000 else if (path == "/v1/browser") 20000 else 12000
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
    fun close() { closed = true; disconnect() }
    fun disconnect() {
        val snapshot = synchronized(active) { active.toList() }
        for (connection in snapshot) connection.disconnect()
    }
}
class GatewayFailure(val status: Int) : Exception("Gateway request failed")
