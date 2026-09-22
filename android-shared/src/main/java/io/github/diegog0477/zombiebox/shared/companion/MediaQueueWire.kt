package io.github.diegog0477.zombiebox.shared.companion

import org.json.JSONArray
import org.json.JSONObject

data class WireMediaQueue(val phase: String, val index: Int, val count: Int, val title: String)

/** Additive queue API; all requests retain the companion proof/authentication boundary. */
class MediaQueueWire(private val transport: CompanionTransport) {
    fun status(): WireMediaQueue = read(transport.request("GET", PATH))

    fun start(id: String, urls: List<String>): WireMediaQueue {
        val items = JSONArray()
        for (url in urls) items.put(JSONObject().put("url", url).put("title", ""))
        return read(transport.request("POST", PATH, JSONObject().put("id", id).put("items", items)))
    }

    fun stop() {
        transport.request("DELETE", PATH)
    }

    private fun read(value: JSONObject): WireMediaQueue =
        WireMediaQueue(
            value.getString("phase"),
            value.optInt("index"),
            value.optInt("count"),
            value.optString("title"),
        )

    companion object {
        private const val PATH = "/v1/companion/media/queue"
    }
}
