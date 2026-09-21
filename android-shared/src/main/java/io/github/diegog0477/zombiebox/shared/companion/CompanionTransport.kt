package io.github.diegog0477.zombiebox.shared.companion

import io.github.diegog0477.zombiebox.shared.GatewayApi
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** Prove the saved secret before sending credentials to any candidate address. */
class CompanionTransport(
    private val base: String,
    private val id: String,
    private val token: String,
) {
    private val api = GatewayApi().apply { configure(base, id, token) }

    fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        verify(base, id, token)
        return api.request(method, path, body)
    }

    fun close() = api.close()

    companion object {
        fun address(value: String): String {
            val base = value.trim().trimEnd('/')
            val uri = URI(base)
            require(
                base.length <= 256 &&
                    uri.scheme in listOf("http", "https") &&
                    !uri.host.isNullOrEmpty() &&
                    uri.userInfo == null &&
                    uri.query == null &&
                    uri.fragment == null &&
                    uri.path.isNullOrEmpty()
            )
            return base
        }

        fun verify(base: String, id: String, token: String) {
            require(id.matches(Regex("[0-9a-f]{32}")) && token.matches(Regex("[0-9a-f]{64}")))
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val probe = GatewayApi().apply { this.base = address(base) }
            val received =
                try {
                    probe
                        .request(
                            "POST",
                            "/v1/companion/proof",
                            JSONObject().put("id", id).put("nonce", nonce),
                        )
                        .getString("proof")
                } finally {
                    probe.close()
                }
            val expected = proof(id, token, nonce)
            require(
                MessageDigest.isEqual(
                    expected.toByteArray(Charsets.US_ASCII),
                    received.toByteArray(Charsets.US_ASCII),
                )
            )
        }

        fun proof(id: String, token: String, nonce: String): String {
            val key =
                MessageDigest.getInstance("SHA-256")
                    .digest(token.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
            return mac.doFinal("zombie-companion-v1\n$id\n$nonce".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
}
