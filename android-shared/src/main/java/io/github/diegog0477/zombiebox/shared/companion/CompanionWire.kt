package io.github.diegog0477.zombiebox.shared.companion

import io.github.diegog0477.zombiebox.shared.GatewayApi
import org.json.JSONObject

/** Shared protocol mapping. Presentation never sees JSON or credentials of providers. */
object CompanionWire {
    fun join(gateway: String, code: String, qr: String, name: String): PairingAttempt {
        var address = gateway
        val body = JSONObject().put("name", name.take(80))
        if (qr.isNotEmpty()) {
            require(qr.length <= 1024)
            val payload = JSONObject(qr)
            require(payload.getInt("version") == 1)
            address = payload.getString("gateway")
            val id = payload.getString("invitationId")
            val secret = payload.getString("secret")
            require(id.matches(Regex("[0-9a-f]{32}")) && secret.matches(Regex("[0-9a-f]{64}")))
            body.put("invitationId", id).put("secret", secret)
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
            value.getLong("remainingMs").coerceIn(0, 120000),
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

    fun decide(api: GatewayApi, id: String, accept: Boolean) {
        requireId(id)
        api.request(
            "POST",
            "/v1/device/companions/$id/decision",
            JSONObject().put("accept", accept),
        )
    }

    fun revoke(api: GatewayApi, id: String) {
        requireId(id)
        api.request("DELETE", "/v1/device/companions/$id")
    }

    fun poll(api: GatewayApi, active: Boolean): List<RemoteCommand> {
        val values =
            api.request("POST", "/v1/device/remote/poll", JSONObject().put("active", active))
                .getJSONArray("commands")
        return (0 until minOf(16, values.length())).map {
            val item = values.getJSONObject(it)
            RemoteCommand(
                item.getString("id"),
                item.getString("action"),
                item.optString("provider"),
                item.getLong("remainingMs").coerceIn(0, 2000),
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
        )
    }

    fun send(api: CompanionTransport, action: String, provider: String) {
        api.request(
            "POST",
            "/v1/companion/commands",
            JSONObject().put("action", action).put("provider", provider),
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
