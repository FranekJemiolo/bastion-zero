package com.bastionzero.crypto

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.signature.Signature

/** Ed25519 identity of this node. The secret key must never leave the device. */
class Ed25519KeyPair(val publicKey: ByteArray, val secretKey: ByteArray) {
    init {
        require(publicKey.size == Ed25519.PUBLIC_KEY_SIZE) { "Ed25519 public key must be 32 bytes" }
        require(secretKey.size == Ed25519.SECRET_KEY_SIZE) { "libsodium Ed25519 secret key must be 64 bytes" }
    }
}

/**
 * Ed25519 detached-signature primitive. Behind an interface so the libsodium
 * dependency stays swappable (e.g. for a platform keystore-backed signer).
 */
interface Ed25519 {
    fun generateKeyPair(): Ed25519KeyPair
    fun sign(message: ByteArray, secretKey: ByteArray): ByteArray
    fun verify(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean

    companion object {
        const val PUBLIC_KEY_SIZE = 32
        const val SECRET_KEY_SIZE = 64
        const val SIGNATURE_SIZE = 64
    }
}

/**
 * libsodium-backed implementation. Call [ensureInitialized] once at app start
 * before any other use (the native library must be loaded).
 */
object LibsodiumEd25519 : Ed25519 {

    suspend fun ensureInitialized() {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }
    }

    override fun generateKeyPair(): Ed25519KeyPair {
        val kp = Signature.keypair()
        return Ed25519KeyPair(kp.publicKey.toByteArray(), kp.secretKey.toByteArray())
    }

    override fun sign(message: ByteArray, secretKey: ByteArray): ByteArray =
        Signature.detached(message.toUByteArray(), secretKey.toUByteArray()).toByteArray()

    override fun verify(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != Ed25519.SIGNATURE_SIZE || publicKey.size != Ed25519.PUBLIC_KEY_SIZE) return false
        return try {
            // libsodium bindings signal an invalid signature by throwing.
            Signature.verifyDetached(signature.toUByteArray(), message.toUByteArray(), publicKey.toUByteArray())
            true
        } catch (_: Throwable) {
            false
        }
    }
}
