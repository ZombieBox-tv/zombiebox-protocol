package io.github.diegog0477.zombiebox.shared

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.UUID

/** Bounded, credential-free IPv4 discovery. Run on a worker, never the UI thread. */
class GatewayDiscovery {
    fun scan(): List<DiscoveredGateway> {
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val request = "ZOMBIE_DISCOVER_V1 $nonce\n".toByteArray(Charsets.US_ASCII)
        val destinations = linkedSetOf(InetAddress.getByName("255.255.255.255"))
        val interfaces = NetworkInterface.getNetworkInterfaces()
        var inspected = 0
        while (interfaces != null && interfaces.hasMoreElements() && inspected++ < 32) {
            val network = interfaces.nextElement()
            for (address in network.interfaceAddresses.take(16)) {
                val broadcast = address.broadcast
                if (broadcast is Inet4Address && destinations.size < 16) destinations.add(broadcast)
            }
        }
        val found = linkedMapOf<String, DiscoveredGateway>()
        val socket = DatagramSocket()
        try {
            socket.broadcast = true
            socket.soTimeout = 200
            var sent = false
            for (destination in destinations) {
                try {
                    socket.send(DatagramPacket(request, request.size, destination, 8098))
                    sent = true
                } catch (_: java.io.IOException) {
                    // One unavailable interface must not hide other reachable interfaces.
                }
            }
            check(sent) { "No discovery interface available" }
            val deadline = System.nanoTime() + 2500000000L
            var packets = 0
            while (
                System.nanoTime() < deadline &&
                    packets < 64 &&
                    found.size < 16 &&
                    !Thread.currentThread().isInterrupted
            ) {
                val buffer = ByteArray(128)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    packets++
                    if (packet.port != 8098) continue
                    val candidate =
                        parseReply(
                            String(packet.data, 0, packet.length, Charsets.US_ASCII),
                            nonce,
                            packet.address,
                        )
                    if (candidate != null) found[candidate.address] = candidate
                } catch (_: SocketTimeoutException) {
                    // The overall monotonic deadline controls scan lifetime.
                }
            }
        } finally {
            socket.close()
        }
        return found.values.toList()
    }

    companion object {
        fun parseReply(reply: String, nonce: String, source: InetAddress): DiscoveredGateway? {
            if (
                source !is Inet4Address ||
                    !(source.isSiteLocalAddress ||
                        source.isLinkLocalAddress ||
                        source.isLoopbackAddress)
            )
                return null
            if (!nonce.matches(Regex("[0-9a-f]{32}"))) return null
            val prefix = "ZOMBIE_GATEWAY_V1 $nonce "
            if (!reply.startsWith(prefix) || !reply.endsWith("\n") || reply.length > 96) return null
            val digits = reply.substring(prefix.length, reply.length - 1)
            if (!digits.matches(Regex("[0-9]{1,5}"))) return null
            val port = digits.toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return DiscoveredGateway("http://${source.hostAddress}:$port")
        }
    }
}
