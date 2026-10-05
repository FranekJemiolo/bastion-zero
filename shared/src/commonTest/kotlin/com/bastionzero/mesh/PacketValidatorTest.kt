package com.bastionzero.mesh

import com.bastionzero.proto.SurvivalPacket
import com.bastionzero.testing.FakeEd25519
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.ByteString.Companion.toByteString

class PacketValidatorTest {
    private val ed = FakeEd25519()
    private val keys = ed.generateKeyPair()
    private val signer = PacketSigner(keys, LamportClock(), ed)

    private fun validator() = PacketValidator(ed, ReplayGuard(), LamportClock())

    private fun sos() = signer.create(SurvivalPacket.PacketType.SOS_MEDICAL, 52_123_400, 21_012_300)

    @Test
    fun acceptsFreshSignedPacket() {
        assertEquals(Verdict.ACCEPT, validator().validate(sos()))
    }

    @Test
    fun signatureSurvivesWireRoundTripAndTtlDecrement() {
        val relayed = SurvivalPacket.ADAPTER.decode(sos().encode()).let { it.copy(ttl = it.ttl - 1) }
        assertTrue(relayed.verifySignature(ed))
    }

    @Test
    fun rejectsTamperedPayload() {
        val forged = sos().let { it.copy(payload = "FAKE".encodeToByteArray().toByteString()) }
        assertEquals(Verdict.BAD_SIGNATURE, validator().validate(forged))
    }

    @Test
    fun rejectsExactReplay() {
        val v = validator()
        val p = sos()
        assertEquals(Verdict.ACCEPT, v.validate(p))
        assertEquals(Verdict.DUPLICATE, v.validate(p))
    }

    @Test
    fun rejectsReplayAfterSignatureCacheRotates() {
        val guard = ReplayGuard(cacheCapacity = 2)
        val v = PacketValidator(ed, guard, LamportClock())
        val old = sos()
        v.validate(old)
        repeat(100) { v.validate(sos()) } // pushes `old` out of the cache AND out of the window
        assertEquals(Verdict.STALE, v.validate(old))
    }

    @Test
    fun acceptsOutOfOrderArrivalInsideWindow() {
        val v = validator()
        val first = sos()
        val second = sos()
        assertEquals(Verdict.ACCEPT, v.validate(second))
        assertEquals(Verdict.ACCEPT, v.validate(first))
        assertEquals(Verdict.DUPLICATE, v.validate(first))
    }

    @Test
    fun forgedPacketCannotAdvanceVictimWindow() {
        val v = validator()
        val genuine = sos()
        val forged = genuine.copy(lamport = 1_000_000, payload = "X".encodeToByteArray().toByteString())
        assertEquals(Verdict.BAD_SIGNATURE, v.validate(forged))
        assertEquals(Verdict.ACCEPT, v.validate(genuine))
    }

    @Test
    fun rejectsMalformed() {
        val p = sos().copy(signature = okio.ByteString.EMPTY)
        assertEquals(Verdict.MALFORMED, validator().validate(p))
        assertFalse(p.verifySignature(ed))
    }

    @Test
    fun validatingAdvancesLocalClockPastRemote() {
        val local = LamportClock()
        val v = PacketValidator(ed, ReplayGuard(), local)
        val p = sos()
        v.validate(p)
        assertTrue(local.value > p.lamport)
    }
}
