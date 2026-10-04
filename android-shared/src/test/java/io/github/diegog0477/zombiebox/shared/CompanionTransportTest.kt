package io.github.diegog0477.zombiebox.shared

import io.github.diegog0477.zombiebox.shared.companion.CompanionTransport
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionTransportTest {
    @Test
    fun validProofAllowsStatusWithConfiguredBaseAndExactCredentials() {
        Fixture(validProof = true).use { fixture ->
            val transport = CompanionTransport(fixture.base, DEVICE_ID, TOKEN)
            try {
                val status = transport.request("GET", "/v1/companion/status")

                assertTrue(status.getBoolean("active"))
                assertEquals(listOf("proof", "status"), fixture.events)
                assertEquals(1, fixture.statusRequests.get())
                assertEquals("Bearer $TOKEN", fixture.statusAuthorization.get())
                assertEquals(DEVICE_ID, fixture.statusDevice.get())
                assertEquals(DEVICE_ID, fixture.proofDeviceId.get())
                assertNull(fixture.proofAuthorization.get())
                assertNull(fixture.proofDevice.get())
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun incorrectProofPreventsStatusRequestAndCredentialDisclosure() {
        Fixture(validProof = false).use { fixture ->
            val transport = CompanionTransport(fixture.base, DEVICE_ID, TOKEN)
            try {
                assertThrows(IllegalArgumentException::class.java) {
                    transport.request("GET", "/v1/companion/status")
                }

                assertEquals(listOf("proof"), fixture.events)
                assertEquals(0, fixture.statusRequests.get())
                assertNull(fixture.statusAuthorization.get())
                assertNull(fixture.statusDevice.get())
                assertNull(fixture.proofAuthorization.get())
                assertNull(fixture.proofDevice.get())
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun validProofAllowsUploadWithConfiguredBaseAndExactCredentials() {
        Fixture(validProof = true).use { fixture ->
            val transport = CompanionTransport(fixture.base, DEVICE_ID, TOKEN)
            val mediaId = "c".repeat(32)
            val payload = byteArrayOf(1, 2, 3, 4)
            try {
                val result =
                    transport.upload(mediaId, payload.size, ByteArrayInputStream(payload)) {}

                assertEquals("accepted", result.getString("status"))
                assertEquals(listOf("proof", "upload"), fixture.events)
                assertEquals("/v1/companion/media/$mediaId", fixture.uploadPath.get())
                assertEquals("Bearer $TOKEN", fixture.uploadAuthorization.get())
                assertEquals(DEVICE_ID, fixture.uploadDevice.get())
                assertArrayEquals(payload, fixture.uploadBody.get())
                assertNull(fixture.proofAuthorization.get())
                assertNull(fixture.proofDevice.get())
            } finally {
                transport.close()
            }
        }
    }

    private class Fixture(private val validProof: Boolean) : AutoCloseable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val base = "http://127.0.0.1:${server.localPort}"
        val events = CopyOnWriteArrayList<String>()
        val statusRequests = AtomicInteger()
        val proofAuthorization = AtomicReference<String?>()
        val proofDevice = AtomicReference<String?>()
        val proofDeviceId = AtomicReference<String?>()
        val statusAuthorization = AtomicReference<String?>()
        val statusDevice = AtomicReference<String?>()
        val uploadAuthorization = AtomicReference<String?>()
        val uploadDevice = AtomicReference<String?>()
        val uploadPath = AtomicReference<String?>()
        val uploadBody = AtomicReference<ByteArray?>()
        private val serverThread = Thread({ serve() }, "companion-transport-test-server")

        init {
            serverThread.isDaemon = true
            serverThread.start()
        }

        private fun serve() {
            while (!server.isClosed) {
                val client =
                    try {
                        server.accept()
                    } catch (_: SocketException) {
                        return
                    }
                try {
                    client.use { handle(it) }
                } catch (_: IOException) {
                    // A client disconnect closes this one-request test connection.
                }
            }
        }

        private fun handle(client: Socket) {
            client.soTimeout = 5000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ', limit = 3)
            if (parts.size < 2) return

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] =
                        line.substring(separator + 1).trim()
                }
            }
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = readExact(input, contentLength)
            val path = parts[1]

            when (path) {
                "/v1/companion/proof" -> {
                    events.add("proof")
                    proofAuthorization.set(headers["authorization"])
                    proofDevice.set(headers["x-zombie-device"])
                    val request = JSONObject(String(body, UTF_8))
                    val deviceId = request.getString("id")
                    proofDeviceId.set(deviceId)
                    val nonce = request.getString("nonce")
                    val proof =
                        if (validProof) CompanionTransport.proof(deviceId, TOKEN, nonce)
                        else "0".repeat(64)
                    respond(output, 200, JSONObject().put("proof", proof).toString())
                }
                "/v1/companion/status" -> {
                    events.add("status")
                    statusRequests.incrementAndGet()
                    statusAuthorization.set(headers["authorization"])
                    statusDevice.set(headers["x-zombie-device"])
                    respond(output, 200, JSONObject().put("active", true).toString())
                }
                else -> {
                    if (path.startsWith("/v1/companion/media/")) {
                        events.add("upload")
                        uploadAuthorization.set(headers["authorization"])
                        uploadDevice.set(headers["x-zombie-device"])
                        uploadPath.set(path)
                        uploadBody.set(body)
                        respond(output, 201, JSONObject().put("status", "accepted").toString())
                    } else {
                        respond(output, 404, JSONObject().put("error", "not found").toString())
                    }
                }
            }
        }

        override fun close() {
            server.close()
            serverThread.join(1000)
        }

        private fun readLine(input: BufferedInputStream): String? {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value < 0) {
                    return if (bytes.size() == 0) null else String(bytes.toByteArray(), UTF_8)
                }
                if (value == '\r'.code) {
                    val next = input.read()
                    if (next == '\n'.code) return String(bytes.toByteArray(), UTF_8)
                    bytes.write(value)
                    if (next >= 0) bytes.write(next)
                } else {
                    bytes.write(value)
                }
            }
        }

        private fun readExact(input: BufferedInputStream, size: Int): ByteArray {
            val bytes = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val count = input.read(bytes, offset, size - offset)
                if (count < 0) throw IOException("Unexpected end of HTTP request body")
                offset += count
            }
            return bytes
        }
    }

    private companion object {
        val DEVICE_ID = "a".repeat(32)
        val TOKEN = "b".repeat(64)
    }
}

private fun respond(output: BufferedOutputStream, status: Int, body: String) {
    val bytes = body.toByteArray(UTF_8)
    val statusText = if (status == 201) "Created" else if (status == 200) "OK" else "Not Found"
    val headers =
        "HTTP/1.1 $status $statusText\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
    output.write(headers.toByteArray(UTF_8))
    output.write(bytes)
    output.flush()
}
