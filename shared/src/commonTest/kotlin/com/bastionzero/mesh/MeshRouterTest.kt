package com.bastionzero.mesh

import com.bastionzero.crdt.MapPinStore
import com.bastionzero.crdt.PinKind
import com.bastionzero.crypto.Ed25519KeyPair
import com.bastionzero.haptics.HapticChord
import com.bastionzero.haptics.HapticPlayer
import com.bastionzero.power.PowerBackend
import com.bastionzero.power.PowerGovernor
import com.bastionzero.power.PowerInputs
import com.bastionzero.proto.SurvivalPacket
import com.bastionzero.testing.FakeEd25519
import kotlin.test.BeforeTest
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class FakeMeshTransport : MeshTransport {
    val broadcasted = mutableListOf<SurvivalPacket>()
    private val _incoming = MutableSharedFlow<SurvivalPacket>(replay = 16, extraBufferCapacity = 64)
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
    private lateinit var ed: FakeEd25519
    private lateinit var senderKeys: Ed25519KeyPair
    private lateinit var remoteKeys: Ed25519KeyPair

    private lateinit var localClock: LamportClock
    private lateinit var remoteClock: LamportClock

    private lateinit var localSigner: PacketSigner
    private lateinit var remoteSigner: PacketSigner

    private lateinit var validator: PacketValidator
    private lateinit var pinStore: MapPinStore
    private lateinit var haptics: FakeHapticPlayer
    private lateinit var transport: FakeMeshTransport

    @BeforeTest
    fun setUp() {
        ed = FakeEd25519()
        senderKeys = ed.generateKeyPair()
        remoteKeys = ed.generateKeyPair()

        localClock = LamportClock()
        remoteClock = LamportClock()

        localSigner = PacketSigner(senderKeys, localClock, ed)
        remoteSigner = PacketSigner(remoteKeys, remoteClock, ed)

        validator = PacketValidator(ed, ReplayGuard(), localClock)
        pinStore = MapPinStore("local_node", localClock)
        haptics = FakeHapticPlayer()
        transport = FakeMeshTransport()
    }

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
        runCurrent()
        val packet = remoteSigner.create(
            type = SurvivalPacket.PacketType.PING,
            ttl = 5,
        )

        transport.simulateIncoming(packet)
        advanceUntilIdle()

        assertEquals(1, transport.broadcasted.size)
        val forwarded = transport.broadcasted.first()
        assertEquals(4, forwarded.ttl)
        assertEquals(packet.packetId, forwarded.packetId)
        assertEquals(packet.signature, forwarded.signature)
    }

    @Test
    fun haltsFloodWhenTtlIsOne() = runTest {
        val router = createRouter(backgroundScope)
        runCurrent()
        val packet = remoteSigner.create(
            type = SurvivalPacket.PacketType.PING,
            ttl = 1,
        )

        transport.simulateIncoming(packet)
        advanceUntilIdle()

        assertEquals(0, transport.broadcasted.size)
    }

    @Test
    fun dropsReplayedPacketsWithoutForwarding() = runTest {
        val router = createRouter(backgroundScope)
        runCurrent()
        val packet = remoteSigner.create(
            type = SurvivalPacket.PacketType.PING,
            ttl = 4,
        )

        transport.simulateIncoming(packet)
        advanceUntilIdle()
        assertEquals(1, transport.broadcasted.size)

        // Simulate duplicate arrival
        transport.simulateIncoming(packet)
        advanceUntilIdle()
        assertEquals(1, transport.broadcasted.size) // No second broadcast
    }

    @Test
    fun incomingSosTriggersHapticAlarm() = runTest {
        val router = createRouter(backgroundScope)
        runCurrent()
        val sos = remoteSigner.create(
            type = SurvivalPacket.PacketType.SOS_MEDICAL,
            ttl = 3,
        )

        transport.simulateIncoming(sos)
        advanceUntilIdle()

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
