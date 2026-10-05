package com.bastionzero.mesh

import com.bastionzero.crdt.MapPin
import com.bastionzero.crdt.MapPinStore
import com.bastionzero.crdt.PinKind
import com.bastionzero.crdt.Stamp
import com.bastionzero.haptics.HapticChord
import com.bastionzero.haptics.HapticPlayer
import com.bastionzero.power.PowerGovernor
import com.bastionzero.proto.SurvivalPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * Coordinates flood routing, cryptographic verification, CRDT map pin synchronization,
 * and haptic alarms across the BLE mesh network.
 */
class MeshRouter(
    val transport: MeshTransport,
    val signer: PacketSigner,
    val validator: PacketValidator,
    val clock: LamportClock,
    val pinStore: MapPinStore,
    val haptics: HapticPlayer,
    val powerGovernor: PowerGovernor,
    scope: CoroutineScope,
) {
    private val _recentPackets = MutableStateFlow<List<SurvivalPacket>>(emptyList())
    val recentPackets: StateFlow<List<SurvivalPacket>> = _recentPackets.asStateFlow()

    private val _pins = MutableStateFlow<List<MapPin>>(emptyList())
    val pins: StateFlow<List<MapPin>> = _pins.asStateFlow()

    val peerCount: StateFlow<Int> = transport.peerCount

    init {
        // Ingest incoming packets from BLE transport
        transport.incomingPackets
            .onEach { onPacketReceived(it) }
            .launchIn(scope)

        updatePins()
    }

    fun start() {
        transport.start()
    }

    fun stop() {
        transport.stop()
    }

    /**
     * Inbound validation and routing pipeline:
     * 1. Validate against signature & anti-replay window.
     * 2. If valid, update local CRDT state or trigger emergency haptics.
     * 3. Flood-route forward if TTL > 1.
     */
    fun onPacketReceived(packet: SurvivalPacket) {
        val verdict = validator.validate(packet)
        if (verdict != Verdict.ACCEPT) {
            return
        }

        // Keep last 50 verified packets for tactical review
        _recentPackets.update { (listOf(packet) + it).take(50) }

        when (packet.type) {
            SurvivalPacket.PacketType.SOS_MEDICAL,
            SurvivalPacket.PacketType.SOS_RESCUE -> {
                haptics.play(HapticChord.MEDICAL_SOS)
            }
            SurvivalPacket.PacketType.HAZARD_PIN -> {
                haptics.play(HapticChord.HAZARD_APPROACHING)
                decodePin(packet)?.let { (pin, stamp) ->
                    pinStore.applyAdd(pin, stamp)
                    updatePins()
                }
            }
            SurvivalPacket.PacketType.RESOURCE_PIN -> {
                decodePin(packet)?.let { (pin, stamp) ->
                    pinStore.applyAdd(pin, stamp)
                    updatePins()
                }
            }
            SurvivalPacket.PacketType.PIN_REMOVE -> {
                val pinId = packet.payload.utf8()
                val stamp = Stamp(packet.lamport, packet.senderId.hex())
                pinStore.applyRemove(pinId, stamp)
                updatePins()
            }
            SurvivalPacket.PacketType.PIN_CONFIRM -> {
                val pinId = packet.payload.utf8()
                val replica = packet.senderId.hex()
                pinStore.applyConfirm(pinId, replica)
                updatePins()
            }
            SurvivalPacket.PacketType.PING -> {
                // Keep-alive or discovery ping; clock already advanced by validator
            }
            null -> Unit
        }

        // Flood Routing: decrement TTL and forward to other nodes
        if (packet.ttl > 1) {
            val forwarded = packet.copy(ttl = packet.ttl - 1)
            transport.broadcast(forwarded)
        }
    }

    /** Broadcast a medical or rescue distress signal across the mesh. */
    fun broadcastSos(type: SurvivalPacket.PacketType, latE6: Int = 0, lonE6: Int = 0, note: String = "") {
        val packet = signer.create(
            type = type,
            latE6 = latE6,
            lonE6 = lonE6,
            payload = note.encodeToByteArray(),
        )
        _recentPackets.update { (listOf(packet) + it).take(50) }
        transport.broadcast(packet)
    }

    /** Drop a hazard or resource pin on the offline map and broadcast to mesh. */
    fun dropPin(kind: PinKind, label: String, latE6: Int, lonE6: Int): MapPin {
        val pinId = "pin_${clock.value}_${signer.senderId.take(4).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}"
        val pin = MapPin(
            id = pinId,
            kind = kind,
            label = label,
            latE6 = latE6,
            lonE6 = lonE6,
        )
        pinStore.add(pin)
        updatePins()

        val packetType = when (kind) {
            PinKind.HAZARD -> SurvivalPacket.PacketType.HAZARD_PIN
            PinKind.RESOURCE -> SurvivalPacket.PacketType.RESOURCE_PIN
        }
        val payloadBytes = "${pin.id}|${pin.label}".encodeToByteArray()
        val packet = signer.create(
            type = packetType,
            latE6 = latE6,
            lonE6 = lonE6,
            payload = payloadBytes,
        )
        _recentPackets.update { (listOf(packet) + it).take(50) }
        transport.broadcast(packet)
        return pin
    }

    /** Confirm an observed pin to increase its verification count. */
    fun confirmPin(pinId: String) {
        pinStore.confirm(pinId)
        updatePins()

        val packet = signer.create(
            type = SurvivalPacket.PacketType.PIN_CONFIRM,
            payload = pinId.encodeToByteArray(),
        )
        _recentPackets.update { (listOf(packet) + it).take(50) }
        transport.broadcast(packet)
    }

    /** Remove a stale or resolved pin from the network. */
    fun removePin(pinId: String) {
        pinStore.remove(pinId)
        updatePins()

        val packet = signer.create(
            type = SurvivalPacket.PacketType.PIN_REMOVE,
            payload = pinId.encodeToByteArray(),
        )
        _recentPackets.update { (listOf(packet) + it).take(50) }
        transport.broadcast(packet)
    }

    fun confirmationCount(pinId: String): Int = pinStore.confirmationCount(pinId)

    private fun updatePins() {
        _pins.value = pinStore.pins()
    }

    private fun decodePin(packet: SurvivalPacket): Pair<MapPin, Stamp>? {
        val raw = packet.payload.utf8()
        val parts = raw.split("|", limit = 2)
        if (parts.size < 2) return null
        val kind = if (packet.type == SurvivalPacket.PacketType.HAZARD_PIN) PinKind.HAZARD else PinKind.RESOURCE
        val pin = MapPin(
            id = parts[0],
            kind = kind,
            label = parts[1],
            latE6 = packet.latE6,
            lonE6 = packet.lonE6,
        )
        val stamp = Stamp(packet.lamport, packet.senderId.hex())
        return pin to stamp
    }
}
