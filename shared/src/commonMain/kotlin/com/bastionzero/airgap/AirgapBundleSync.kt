package com.bastionzero.airgap

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class QrChunk(
    val bundleId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val payloadBase64: String,
    val crc32: Int,
)

data class IngestStatus(
    val bundleId: String,
    val chunksReceived: Int,
    val totalChunks: Int,
    val isComplete: Boolean,
    val assembledData: ByteArray? = null,
)

/**
 * Air-gapped Optical QR Stream Synchronization.
 * Chunks arbitrary binary payloads (e.g. CRDT hazard pins, trauma records)
 * into high-density sequential QR frames for 100% RF-silent (OPSEC) peer data transfer.
 */
@OptIn(ExperimentalEncodingApi::class)
class AirgapBundleSync {

    /**
     * Slice binary data into QR-transmittable frames.
     */
    fun encodeBundle(bundleId: String, data: ByteArray, maxChunkBytes: Int = 64): List<String> {
        require(data.isNotEmpty()) { "Cannot encode empty bundle" }
        require(maxChunkBytes > 0) { "Chunk size must be positive" }

        val totalChunks = (data.size + maxChunkBytes - 1) / maxChunkBytes
        val chunks = mutableListOf<String>()

        for (i in 0 until totalChunks) {
            val start = i * maxChunkBytes
            val end = (start + maxChunkBytes).coerceAtMost(data.size)
            val slice = data.copyOfRange(start, end)
            val b64 = Base64.encode(slice)
            val crc = computeCrc(slice)
            val frame = "BZ:$bundleId:$i:$totalChunks:$b64:$crc"
            chunks.add(frame)
        }

        return chunks
    }

    /**
     * Parse an encoded QR frame string into a [QrChunk] or null if corrupted.
     */
    fun parseFrame(frame: String): QrChunk? {
        val parts = frame.split(":")
        if (parts.size != 6 || parts[0] != "BZ") return null

        val bundleId = parts[1]
        val index = parts[2].toIntOrNull() ?: return null
        val total = parts[3].toIntOrNull() ?: return null
        val b64 = parts[4]
        val crc = parts[5].toIntOrNull() ?: return null

        val decoded = try {
            Base64.decode(b64)
        } catch (_: Throwable) {
            return null
        }

        if (computeCrc(decoded) != crc) return null

        return QrChunk(
            bundleId = bundleId,
            chunkIndex = index,
            totalChunks = total,
            payloadBase64 = b64,
            crc32 = crc,
        )
    }

    private fun computeCrc(data: ByteArray): Int {
        var crc = 0x5A5A
        for (b in data) {
            crc = ((crc shl 5) - crc) + (b.toInt() and 0xFF)
        }
        return crc
    }
}

/**
 * Accumulator for ingesting animated QR frames from camera stream.
 * Smoothly reconstructs payloads arriving out-of-order.
 */
@OptIn(ExperimentalEncodingApi::class)
class AirgapBundleReceiver(
    private val sync: AirgapBundleSync = AirgapBundleSync(),
) {
    private var targetBundleId: String? = null
    private var expectedTotal: Int = 0
    private val receivedChunks = mutableMapOf<Int, ByteArray>()

    fun reset() {
        targetBundleId = null
        expectedTotal = 0
        receivedChunks.clear()
    }

    /**
     * Ingest a scanned QR frame. Returns updated ingestion progress.
     */
    fun ingestFrame(rawFrame: String): IngestStatus? {
        val chunk = sync.parseFrame(rawFrame) ?: return null

        if (targetBundleId == null) {
            targetBundleId = chunk.bundleId
            expectedTotal = chunk.totalChunks
        } else if (chunk.bundleId != targetBundleId) {
            // Frame belongs to a different bundle
            return null
        }

        val decoded = try {
            Base64.decode(chunk.payloadBase64)
        } catch (_: Throwable) {
            return null
        }

        receivedChunks[chunk.chunkIndex] = decoded

        val isComplete = receivedChunks.size == expectedTotal
        val assembled = if (isComplete) {
            val totalBytes = (0 until expectedTotal).sumOf { receivedChunks[it]?.size ?: 0 }
            val fullData = ByteArray(totalBytes)
            var offset = 0
            for (i in 0 until expectedTotal) {
                val part = receivedChunks[i] ?: error("Missing part $i")
                part.copyInto(fullData, destinationOffset = offset)
                offset += part.size
            }
            fullData
        } else null

        return IngestStatus(
            bundleId = chunk.bundleId,
            chunksReceived = receivedChunks.size,
            totalChunks = expectedTotal,
            isComplete = isComplete,
            assembledData = assembled,
        )
    }
}
