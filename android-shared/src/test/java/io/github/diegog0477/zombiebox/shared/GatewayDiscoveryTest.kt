package io.github.diegog0477.zombiebox.shared

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class GatewayDiscoveryTest {
    private val nonce = "a".repeat(32)
    private val source = InetAddress.getByName("192.168.1.7")

    @Test
    fun locatorUsesPacketSourceAndAdvertisedPort() {
        assertEquals(
            DiscoveredGateway("http://192.168.1.7:8090"),
            GatewayDiscovery.parseReply("ZOMBIE_GATEWAY_V1 $nonce 8090\n", nonce, source),
        )
    }

    @Test
    fun rejectsForeignNonceInjectionInvalidPortAndPublicSource() {
        for (reply in
            listOf(
                "ZOMBIE_GATEWAY_V1 ${"b".repeat(32)} 8090\n",
                "ZOMBIE_GATEWAY_V1 $nonce 8090\nextra",
                "ZOMBIE_GATEWAY_V1 $nonce 0\n",
                "ZOMBIE_GATEWAY_V1 $nonce 65536\n",
                "ZOMBIE_GATEWAY_V1 $nonce http://example.test\n",
            )) {
            assertNull(GatewayDiscovery.parseReply(reply, nonce, source))
        }
        assertNull(
            GatewayDiscovery.parseReply(
                "ZOMBIE_GATEWAY_V1 $nonce 8090\n",
                nonce,
                InetAddress.getByName("8.8.8.8"),
            )
        )
    }
}
