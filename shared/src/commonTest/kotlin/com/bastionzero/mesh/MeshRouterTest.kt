package com.bastionzero.mesh

import com.bastionzero.crdt.MapPinStore
import com.bastionzero.crdt.PinKind
import com.bastionzero.haptics.HapticChord
import com.bastionzero.haptics.HapticPlayer
import com.bastionzero.power.PowerBackend
import com.bastionzero.power.PowerGovernor
import com.bastionzero.power.PowerInputs
import com.bastionzero.proto.SurvivalPacket
import com.bastionzero.testing.FakeEd25519
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class FakeMeshTransport : MeshTransport {
    val broadcasted = mutableListOf<SurvivalPacket>()
    private val _incoming = MutableSharedFlow<SurvivalPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<SurvivalPacket> = _incoming

    override val peerCount = MutableStateFlow(1)
    override val isScanning = MutableStateFlow(true)
    override val isAdvertising = MutableStateFlow(true)

    override fun start() {}
    override fun stop() {}

    override fun broadcast(packet: SurvivalPacket) {
        broadcasted.add(packet)
    }

    suspend fun simulateIncoming(packet: SurvivalPacket) {
        _incoming.emit(packet)
    }
}

class FakeHapticPlayer : HapticPlayer {
    val played = mutableListOf<HapticChord>()
    override fun play(chord: HapticChord) {
        played.add(chord)
    }
}

class FakePowerBackend : PowerBackend {
    override val inputs = MutableStateFlow(PowerInputs(100, false, true))
    override fun start() {}
    override fun stop() {}
}

class MeshRouterTest {
    private val ed = FakeEd25519()
    private val senderKeys = ed.generateKeyPair()
    private val remoteKeys = ed.generateKeyPair()

    private val localClock = LamportClock()
    private val remoteClock = LamportClock()

    private val localSigner = PacketSigner(senderKeys, localClock, ed)
    private val remoteSigner = PacketSigner(remoteKeys, remoteClock, ed)

    private val validator = PacketValidator(ed, ReplayGuard(), localClock)
    private val pinStore = MapPinStore("local_node", localClock)
    private val haptics = FakeHapticPlayer()
    private val transport = FakeMeshTransport()

    private fun createRouter(scope: CoroutineScope): MeshRouter {
        val gov = PowerGovernor(FakePowerBackend(), scope)
        return MeshRouter(
            transport = transport,
            signer = localSigner,
            validator = validator,
            clock = localClock,
            pinStore = pinStore,
            haptics = haptics,
            powerGovernor = gov,
            scope = scope,
        )
    }

    @Test
    fun floodRoutesValidPacketWithDecrementedTtl() = runTest {
        val router = createRouter(backgroundScope)
        val packet = remoteSigner.create(
            type = SurvivalPacket.PacketType.PING,
            ttl = 5,
        )

        transport.simulateIncoming(packet)
        testScheduler.advanceUntilIdle()

        assertEquals(1, transport.broadcasted.size)
        val forwarded = transport.broadcasted.first()
        assertEquals(4, forwarded.ttl)
        assertEquals(packet.packetId, forwarded.packetId)
        assertEquals(packet.signature, forwarded.signature)
    }

    @Test
    fun haltsFloodWhenTtlIsOne() = runTest {
        val router = createRouter(backgroundScope)
        val packet = remoteSigner.create(
            type = SurvivalPacket.PacketType.PING,
            ttl = 1,
        )

        transport.simulateIncoming(packet)
        testScheduler.advanceUntilIdle()

        assertEquals(0, transport.broadcasted.size)
    }

    @Test
    fun dropsReplayedPacketsWithoutForwarding() = runTest {
        val router = createRouter(backgroundScope)
        val packet = remoteSigner.create(
            type = SurvivalPacket.PacketType.PING,
            ttl = 4,
        )

        transport.simulateIncoming(packet)
        testScheduler.advanceUntilIdle()
        assertEquals(1, transport.broadcasted.size)

        // Simulate duplicate arrival
        transport.simulateIncoming(packet)
        testScheduler.advanceUntilIdle()
        assertEquals(1, transport.broadcasted.size) // No second broadcast
    }

    @Test
    fun incomingSosTriggersHapticAlarm() = runTest {
        val router = createRouter(backgroundScope)
        val sos = remoteSigner.create(
            type = SurvivalPacket.PacketType.SOS_MEDICAL,
            ttl = 3,
        )

        transport.simulateIncoming(sos)
        testScheduler.advanceUntilIdle()

        assertTrue(haptics.played.contains(HapticChord.MEDICAL_SOS))
    }

    @Test
    fun droppingPinSignsBroadcastsAndStoresInCrdt() = runTest {
        val router = createRouter(backgroundScope)
        val pin = router.dropPin(PinKind.HAZARD, "Downed Wire", 52_000_000, 21_000_000)

        assertNotNull(pin)
        assertEquals(1, router.pins.value.size)
        assertEquals(1, transport.broadcasted.size)

        val broadcastPacket = transport.broadcasted.first()
        assertEquals(SurvivalPacket.PacketType.HAZARD_PIN, broadcastPacket.type)
        assertTrue(broadcastPacket.verifySignature(ed))
    }
}
