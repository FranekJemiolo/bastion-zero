package com.bastionzero.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Exercises the real libsodium binding (not the fake). */
class LibsodiumEd25519Test {
    @Test
    fun signVerifyRoundTrip() = runTest {
        LibsodiumEd25519.ensureInitialized()
        val kp = LibsodiumEd25519.generateKeyPair()
        val msg = "SOS".encodeToByteArray()
        val sig = LibsodiumEd25519.sign(msg, kp.secretKey)
        assertEquals(Ed25519.SIGNATURE_SIZE, sig.size)
        assertTrue(LibsodiumEd25519.verify(sig, msg, kp.publicKey))
        assertFalse(LibsodiumEd25519.verify(sig, "SOT".encodeToByteArray(), kp.publicKey))
    }

    @Test
    fun signaturesAreDeterministic() = runTest {
        LibsodiumEd25519.ensureInitialized()
        val kp = LibsodiumEd25519.generateKeyPair()
        val m = byteArrayOf(1, 2, 3)
        assertContentEquals(LibsodiumEd25519.sign(m, kp.secretKey), LibsodiumEd25519.sign(m, kp.secretKey))
    }
}
