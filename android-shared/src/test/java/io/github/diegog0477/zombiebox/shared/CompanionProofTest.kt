package io.github.diegog0477.zombiebox.shared

import io.github.diegog0477.zombiebox.shared.companion.CompanionTransport
import org.junit.Assert.*
import org.junit.Test

class CompanionProofTest {
    @Test
    fun gatewayProofMatchesFixedCrossLanguageVector() {
        assertEquals(
            "c3f42a4febd5cb190465ae626aadae2fd5d4cc481209659cdb52902beaaa4711",
            CompanionTransport.proof("a".repeat(32), "b".repeat(64), "c".repeat(32)),
        )
        assertNotEquals(
            CompanionTransport.proof("a".repeat(32), "b".repeat(64), "c".repeat(32)),
            CompanionTransport.proof("a".repeat(32), "b".repeat(64), "d".repeat(32)),
        )
    }

    @Test
    fun rejectCredentialBearingOrNonHttpGatewayLocators() {
        for (value in
            listOf(
                "file:///tmp/a",
                "http://user:secret@host",
                "http://host/path",
                "http://host?token=x",
                "http://host#token",
            )) {
            try {
                CompanionTransport.address(value)
                fail(value)
            } catch (_: IllegalArgumentException) {}
        }
        assertEquals(
            "http://192.168.1.4:8090",
            CompanionTransport.address(" http://192.168.1.4:8090/ "),
        )
    }
}
