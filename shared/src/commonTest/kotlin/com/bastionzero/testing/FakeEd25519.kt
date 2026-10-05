package com.bastionzero.testing

import com.bastionzero.crypto.Ed25519
import com.bastionzero.crypto.Ed25519KeyPair
import kotlin.random.Random

/** Deterministic, INSECURE stand-in so mesh logic tests don't need native libsodium. */
class FakeEd25519(seed: Int = 1) : Ed25519 {
    private val rng = Random(seed)

    override fun generateKeyPair(): Ed25519KeyPair {
        val pk = rng.nextBytes(Ed25519.PUBLIC_KEY_SIZE)
        return Ed25519KeyPair(pk, pk + pk)
    }

    override fun sign(message: ByteArray, secretKey: ByteArray): ByteArray =
        mac(message, secretKey.copyOfRange(0, Ed25519.PUBLIC_KEY_SIZE))

    override fun verify(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean =
        signature.contentEquals(mac(message, publicKey))

    private fun mac(message: ByteArray, pk: ByteArray): ByteArray {
        val h = message.contentHashCode() * 31 + pk.contentHashCode()
        return ByteArray(Ed25519.SIGNATURE_SIZE) { i -> (h shr (i % 24)).toByte() }
    }
}
