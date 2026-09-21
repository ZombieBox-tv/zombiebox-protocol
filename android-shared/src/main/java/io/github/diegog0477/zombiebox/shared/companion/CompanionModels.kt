package io.github.diegog0477.zombiebox.shared.companion

/** Semantic pairing values; no Android, provider credentials or UI references. */
data class PairingRequest(
    val id: String,
    val targetId: String,
    val name: String,
    val comparison: String,
    val state: String,
)

data class PairingAttempt(val gateway: String, val request: PairingRequest, val token: String)

data class CompanionGrant(
    val id: String,
    val targetId: String,
    val targetName: String,
    val name: String,
)

data class CompanionStatus(
    val grant: CompanionGrant,
    val remoteOnline: Boolean,
    val castAvailable: Boolean,
    val lastCommand: String,
)

data class PairingInvitation(val code: String, val png: ByteArray, val remainingMs: Long)

data class CompanionInventory(val requests: List<PairingRequest>, val grants: List<CompanionGrant>)

data class RemoteCommand(
    val id: String,
    val action: String,
    val provider: String,
    val remainingMs: Long,
)
