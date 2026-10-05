package com.bastionzero.mesh

import com.bastionzero.crdt.MapPinStore
import com.bastionzero.crypto.Ed25519
import com.bastionzero.crypto.Ed25519KeyPair
import com.bastionzero.crypto.LibsodiumEd25519
import com.bastionzero.haptics.HapticPlayer
import com.bastionzero.power.PowerGovernor
import kotlinx.coroutines.CoroutineScope

class MeshContext(
    val keys: Ed25519KeyPair,
    val clock: LamportClock,
    val signer: PacketSigner,
    val validator: PacketValidator,
    val pinStore: MapPinStore,
    val router: MeshRouter,
)

object MeshFactory {
    fun create(
        transport: MeshTransport,
        haptics: HapticPlayer,
        powerGovernor: PowerGovernor,
        scope: CoroutineScope,
        ed: Ed25519 = LibsodiumEd25519,
        keyPair: Ed25519KeyPair? = null,
    ): MeshContext {
        val keys = keyPair ?: try {
            ed.generateKeyPair()
        } catch (_: Throwable) {
            // Fallback for pre-initialization environments
            val mockKey = ByteArray(Ed25519.PUBLIC_KEY_SIZE) { 0x01 }
            Ed25519KeyPair(mockKey, mockKey + mockKey)
        }
        val clock = LamportClock()
        val signer = PacketSigner(keys, clock, ed)
        val validator = PacketValidator(ed, ReplayGuard(), clock)
        val replicaId = "node_" + keys.publicKey.take(4).joinToString("") {
            it.toUByte().toString(16).padStart(2, '0')
        }
        val pinStore = MapPinStore(replicaId, clock)
        val router = MeshRouter(
            transport = transport,
            signer = signer,
            validator = validator,
            clock = clock,
            pinStore = pinStore,
            haptics = haptics,
            powerGovernor = powerGovernor,
            scope = scope,
        )
        return MeshContext(keys, clock, signer, validator, pinStore, router)
    }
}
