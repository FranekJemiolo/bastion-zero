package com.bastionzero.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureEnclaveKeyManagerTest {

    @Test
    fun testKeyInitializationAndSealingCycle() {
        val manager = SecureEnclaveKeyManager()
        val status = manager.getStatus()
        assertTrue(status.isKeyInitialized)
        assertEquals(false, status.isZeroized)

        val plaintext = "CONFIDENTIAL_TRAUMA_DATA_BLOOD_AB_POS".encodeToByteArray()
        val sealed = manager.sealLocalData(plaintext)
        assertTrue(sealed.size > plaintext.size)

        val unsealed = manager.unsealLocalData(sealed)
        assertNotNull(unsealed)
        assertEquals(plaintext.decodeToString(), unsealed.decodeToString())
    }

    @Test
    fun testPairwiseSessionKeyDerivation() {
        val alice = SecureEnclaveKeyManager("alice")
        val bob = SecureEnclaveKeyManager("bob")

        val aliceKey = alice.getPublicKey()
        val bobKey = bob.getPublicKey()

        val aliceSession = alice.deriveSharedSessionKey(bobKey)
        val bobSession = bob.deriveSharedSessionKey(aliceKey)

        assertEquals(32, aliceSession.size)
        assertEquals(32, bobSession.size)
        // Symmetric pairwise channel
        assertTrue(aliceSession.contentEquals(bobSession))
    }

    @Test
    fun testEmergencyZeroizeDestroysKeys() {
        val manager = SecureEnclaveKeyManager()
        val testData = "TOP_SECRET_COORDINATES".encodeToByteArray()
        val sealed = manager.sealLocalData(testData)

        manager.emergencyZeroize()

        val status = manager.getStatus()
        assertTrue(status.isZeroized)

        assertFailsWith<IllegalStateException> {
            manager.getPublicKey()
        }

        assertNull(manager.unsealLocalData(sealed))
    }
}
