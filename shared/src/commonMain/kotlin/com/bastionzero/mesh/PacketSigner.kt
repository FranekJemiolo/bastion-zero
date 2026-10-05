package com.bastionzero.mesh

import com.bastionzero.crypto.Ed25519
import com.bastionzero.crypto.Ed25519KeyPair
import com.bastionzero.proto.SurvivalPacket
import kotlin.random.Random
import okio.ByteString
import okio.ByteString.Companion.toByteString

/** Canonical bytes covered by the signature: no signature, no hop-mutable TTL. */
fun SurvivalPacket.signingBytes(): ByteArray =
    copy(signature = ByteString.EMPTY, ttl = 0).encode()

/** True iff [signature] is a valid Ed25519 signature by [senderId] over [signingBytes]. */
fun SurvivalPacket.verifySignature(ed: Ed25519): Boolean =
    ed.verify(signature.toByteArray(), signingBytes(), senderId.toByteArray())

/** Creates signed packets for this node, stamping each with the next Lamport tick. */
class PacketSigner(
    private val keys: Ed25519KeyPair,
    private val clock: LamportClock,
    private val ed: Ed25519,
    private val defaultTtl: Int = DEFAULT_TTL,
) {
    val senderId: ByteArray get() = keys.publicKey

    fun create(
        type: SurvivalPacket.PacketType,
        latE6: Int = 0,
        lonE6: Int = 0,
        altitudeDm: Int = 0,
        payload: ByteArray = ByteArray(0),
        wallClockHintSeconds: Long = 0L,
        ttl: Int = defaultTtl,
    ): SurvivalPacket {
        val unsigned = SurvivalPacket(
            packetId = Random.nextLong(),
            lamport = clock.tick(),
            senderId = keys.publicKey.toByteString(),
            type = type,
            latE6 = latE6,
            lonE6 = lonE6,
            altitudeDm = altitudeDm,
            payload = payload.toByteString(),
            ttl = ttl,
            wallClockHint = wallClockHintSeconds,
        )
        val sig = ed.sign(unsigned.signingBytes(), keys.secretKey)
        return unsigned.copy(signature = sig.toByteString())
    }

    companion object {
        const val DEFAULT_TTL = 5
    }
}
