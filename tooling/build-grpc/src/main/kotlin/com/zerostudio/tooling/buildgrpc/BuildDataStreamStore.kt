package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.CompressionKind
import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.TransferRejectReason
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * In-memory transfer store used by BuildSessionGrpcService.
 *
 * This is a phase-B baseline implementation focusing on text/binary source payload transport.
 */
class BuildDataStreamStore {
  private val store = ConcurrentHashMap<String, ByteArray>()

  fun append(chunk: DataChunk): AppendResult {
    if (chunk.transferId.isBlank()) {
      return AppendResult(0, TransferRejectReason.TRANSFER_REJECT_REASON_MISSING_IDENTITY)
    }

    val payload = decompressIfRequired(chunk)
      ?: return AppendResult(0, TransferRejectReason.TRANSFER_REJECT_REASON_DECOMPRESSION_FAILED)

    if (chunk.checksum.size() > 0 && !verifySha256(payload, chunk.checksum.toByteArray())) {
      return AppendResult(0, TransferRejectReason.TRANSFER_REJECT_REASON_CHECKSUM_MISMATCH)
    }

    val merged = merge(store[chunk.transferId], payload)
    store[chunk.transferId] = merged
    return AppendResult(payload.size, TransferRejectReason.TRANSFER_REJECT_REASON_NONE)
  }

  fun read(transferId: String, offset: Long, maxBytes: Long): ByteArray {
    val all = store[transferId] ?: return ByteArray(0)
    val from = offset.coerceAtLeast(0).coerceAtMost(all.size.toLong()).toInt()
    val to = if (maxBytes <= 0) all.size else (from.toLong() + maxBytes).coerceAtMost(all.size.toLong()).toInt()
    return all.copyOfRange(from, to)
  }

  private fun merge(current: ByteArray?, incoming: ByteArray): ByteArray {
    if (current == null || current.isEmpty()) return incoming
    return ByteArray(current.size + incoming.size).also { merged ->
      System.arraycopy(current, 0, merged, 0, current.size)
      System.arraycopy(incoming, 0, merged, current.size, incoming.size)
    }
  }

  private fun verifySha256(payload: ByteArray, expected: ByteArray): Boolean {
    val digest = MessageDigest.getInstance("SHA-256").digest(payload)
    return digest.contentEquals(expected)
  }

  private fun decompressIfRequired(chunk: DataChunk): ByteArray? {
    val payload = chunk.payload.toByteArray()
    return when (chunk.compression) {
      CompressionKind.COMPRESSION_KIND_UNSPECIFIED,
      CompressionKind.COMPRESSION_KIND_NONE,
      CompressionKind.UNRECOGNIZED,
      CompressionKind.COMPRESSION_KIND_ZSTD,
      CompressionKind.COMPRESSION_KIND_LZ4,
      -> payload
      CompressionKind.COMPRESSION_KIND_GZIP -> decompressGzip(payload)
    }
  }

  private fun decompressGzip(payload: ByteArray): ByteArray? = try {
    GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
  } catch (_: Throwable) {
    null
  }

  data class AppendResult(val acceptedBytes: Int, val reason: TransferRejectReason)

  companion object {
    fun checksumFor(payload: ByteArray): ByteString =
      ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(payload))
  }
}
