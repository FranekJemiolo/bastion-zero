package com.bastionzero.net

data class MeshtasticMeshPacket(
    val fromNode: Long,
    val toNode: Long,
    val packetId: Long,
    val hopLimit: Int,
    val portNum: Int,
    val channel: Int,
    val payload: ByteArray,
)

data class MeshtasticDecodedPayload(
    val senderNodeHex: String,
    val portNum: Int,
    val textMessage: String?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Int?,
)

/**
 * Meshtastic Open-Source LoRa Mesh Interoperability Bridge.
 * Transcodes packets between Bastion Zero and civilian Meshtastic networks.
 */
class MeshtasticProtocolBridge {

    companion object {
        const val PORT_TEXT_MESSAGE_APP = 1
        const val PORT_ROUTING_APP = 3
        const val PORT_NODEINFO_APP = 4
        const val PORT_POSITION_APP = 32
        const val BROADCAST_ADDR = 0xFFFFFFFFL
    }

    /**
     * Encode a Bastion Zero survival distress message into a Meshtastic text packet frame.
     */
    fun encodeSurvivalDistressToMeshtastic(
        fromNodeId: Long,
        packetId: Long,
        distressMessage: String,
        hopLimit: Int = 3,
    ): MeshtasticMeshPacket {
        val payloadBytes = distressMessage.encodeToByteArray()
        return MeshtasticMeshPacket(
            fromNode = fromNodeId,
            toNode = BROADCAST_ADDR,
            packetId = packetId,
            hopLimit = hopLimit,
            portNum = PORT_TEXT_MESSAGE_APP,
            channel = 0,
            payload = payloadBytes
        )
    }

    /**
     * Decode an inbound raw Meshtastic packet frame.
     */
    fun decodeMeshtasticPacket(packet: MeshtasticMeshPacket): MeshtasticDecodedPayload {
        val fromHex = "!" + packet.fromNode.toString(16).padStart(8, '0')

        return when (packet.portNum) {
            PORT_TEXT_MESSAGE_APP -> {
                val text = packet.payload.decodeToString()
                MeshtasticDecodedPayload(
                    senderNodeHex = fromHex,
                    portNum = packet.portNum,
                    textMessage = text,
                    latitude = null,
                    longitude = null,
                    altitudeMeters = null
                )
            }
            PORT_POSITION_APP -> {
                // If 8+ bytes, decode lat/lon fixed-point micro-degrees
                if (packet.payload.size >= 8) {
                    val latI = readInt32(packet.payload, 0)
                    val lonI = readInt32(packet.payload, 4)
                    val lat = latI / 10000000.0
                    val lon = lonI / 10000000.0
                    MeshtasticDecodedPayload(
                        senderNodeHex = fromHex,
                        portNum = packet.portNum,
                        textMessage = null,
                        latitude = lat,
                        longitude = lon,
                        altitudeMeters = null
                    )
                } else {
                    MeshtasticDecodedPayload(fromHex, packet.portNum, null, null, null, null)
                }
            }
            else -> MeshtasticDecodedPayload(fromHex, packet.portNum, null, null, null, null)
        }
    }

    private fun readInt32(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)
    }
}
