package io.github.diegog0477.zombiebox.shared.companion

import io.github.diegog0477.zombiebox.shared.GatewayApi
import org.json.JSONObject

/** Shared protocol mapping. Presentation never sees JSON or credentials of providers. */
object CompanionWire {
    fun join(
        gateway: String,
        code: String,
        qr: String,
        name: String,
        targetId: String = "",
        clientKey: String = "",
    ): PairingAttempt {
        var address = gateway
        val body = JSONObject().put("name", name.take(80))
        if (clientKey.isNotEmpty()) {
            require(clientKey.matches(Regex("[0-9a-f]{64}")))
            body.put("clientKey", clientKey)
        }
        if (qr.isNotEmpty()) {
            require(qr.length <= 1024)
            val payload = JSONObject(qr)
            require(payload.getInt("version") in 1..2)
            address = payload.getString("gateway")
            val id = payload.getString("invitationId")
            val secret = payload.getString("secret")
            require(id.matches(Regex("[0-9a-f]{32}")) && secret.matches(Regex("[0-9a-f]{64}")))
            body.put("invitationId", id).put("secret", secret)
        } else if (targetId.isNotEmpty()) {
            require(targetId.matches(Regex("[A-Za-z0-9_-]{8,80}")))
            require(clientKey.isNotEmpty())
            body.put("targetId", targetId)
        } else {
            require(code.matches(Regex("[0-9]{6}")))
            body.put("code", code)
        }
        address = CompanionTransport.address(address)
        val api = GatewayApi().apply { base = address }
        return try {
            val result = api.request("POST", "/v1/companion/join", body)
            PairingAttempt(
                address,
                request(result.getJSONObject("request")),
                result.getString("token"),
            )
        } finally {
            api.close()
        }
    }

    fun targets(gateway: String): List<PairingTarget> {
        val api = GatewayApi().apply { base = CompanionTransport.address(gateway) }
        return try {
            val values = api.request("GET", "/v1/companion/targets").getJSONArray("targets")
            (0 until minOf(32, values.length())).map {
                val value = values.getJSONObject(it)
                PairingTarget(
                    value.getString("id").also {
                        require(it.matches(Regex("[A-Za-z0-9_-]{8,80}")))
                    },
                    value.getString("name").take(120),
                )
            }
        } finally {
            api.close()
        }
    }

    fun await(attempt: PairingAttempt): PairingRequest {
        val api = GatewayApi().apply { base = attempt.gateway }
        return try {
            request(
                api.request(
                    "POST",
                    "/v1/companion/requests/${attempt.request.id}",
                    JSONObject().put("token", attempt.token),
                )
            )
        } finally {
            api.close()
        }
    }

    fun invite(api: GatewayApi): PairingInvitation {
        val value =
            api.request(
                "POST",
                "/v1/device/companions/invitations",
                JSONObject().put("gateway", api.base),
            )
        // Decode PNG at the Android data boundary: transport model retains encoded bytes.
        val encoded = value.getString("qrPng")
        require(encoded.length <= 128000)
        return PairingInvitation(
            value.getString("code"),
            android.util.Base64.decode(encoded, android.util.Base64.DEFAULT),
            value.getLong("remainingMs").coerceIn(0, 300000),
        )
    }

    fun inventory(api: GatewayApi): CompanionInventory {
        val result = api.request("GET", "/v1/device/companions")
        val pending = result.getJSONArray("requests")
        val trusted = result.getJSONArray("grants")
        return CompanionInventory(
            (0 until minOf(64, pending.length())).map { request(pending.getJSONObject(it)) },
            (0 until minOf(128, trusted.length())).map { grant(trusted.getJSONObject(it)) },
        )
    }

    fun decide(api: GatewayApi, id: String, accept: Boolean, ignore24h: Boolean = false) {
        requireId(id)
        api.request(
            "POST",
            "/v1/device/companions/$id/decision",
            JSONObject().put("accept", accept).put("ignore24h", !accept && ignore24h),
        )
    }

    fun revoke(api: GatewayApi, id: String) {
        requireId(id)
        api.request("DELETE", "/v1/device/companions/$id")
    }

    fun poll(api: GatewayApi, active: Boolean, inputId: String = ""): List<RemoteCommand> {
        val values =
            api.request(
                    "POST",
                    "/v1/device/remote/poll",
                    JSONObject().put("active", active).put("inputId", inputId),
                )
                .getJSONArray("commands")
        return (0 until minOf(16, values.length())).map {
            val item = values.getJSONObject(it)
            RemoteCommand(
                item.getString("id"),
                item.getString("action"),
                item.optString("provider"),
                item.getLong("remainingMs").coerceIn(0, 2000),
                item.optString("text").take(1024),
                item.optString("inputId"),
            )
        }
    }

    fun acknowledge(api: GatewayApi, id: String, status: String) {
        requireId(id)
        api.request(
            "POST",
            "/v1/device/remote/ack",
            JSONObject().put("id", id).put("status", status),
        )
    }

    fun status(api: CompanionTransport): CompanionStatus {
        val value = api.request("GET", "/v1/companion/status")
        return CompanionStatus(
            grant(value.getJSONObject("grant")),
            value.optBoolean("remoteOnline"),
            value.optBoolean("castAvailable"),
            value.optJSONObject("lastCommand")?.optString("status") ?: "",
            value.optString("textInputId"),
        )
    }

    fun send(api: CompanionTransport, action: String, provider: String) {
        api.request(
            "POST",
            "/v1/companion/commands",
            JSONObject().put("action", action).put("provider", provider),
        )
    }

    fun sendText(api: CompanionTransport, text: String, inputId: String) {
        requireId(inputId)
        require(text.isNotEmpty() && text.length <= 512 && text.none { it.isISOControl() })
        api.request(
            "POST",
            "/v1/companion/commands",
            JSONObject().put("action", "TEXT").put("text", text).put("inputId", inputId),
        )
    }

    private fun requireId(id: String) = require(id.matches(Regex("[0-9a-f]{32}")))

    private fun request(value: JSONObject) =
        PairingRequest(
            value.getString("id").also { requireId(it) },
            value.getString("targetId"),
            value.getString("name"),
            value.getString("comparison"),
            value.getString("state"),
        )

    private fun grant(value: JSONObject) =
        CompanionGrant(
            value.getString("id").also { requireId(it) },
            value.getString("targetId"),
            value.getString("targetName"),
            value.getString("name"),
        )
}
