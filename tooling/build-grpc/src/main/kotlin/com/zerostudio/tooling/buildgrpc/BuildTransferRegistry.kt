package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.TransferRejectReason
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks transfer-level identity and sequencing constraints.
 */
class BuildTransferRegistry {
  private val uploads = ConcurrentHashMap<String, UploadState>()

  fun validateUploadChunk(chunk: DataChunk): TransferRejectReason {
    if (chunk.buildId.isBlank() || chunk.transferId.isBlank()) {
      return TransferRejectReason.TRANSFER_REJECT_REASON_MISSING_IDENTITY
    }

    val key = key(chunk.buildId, chunk.transferId)
    val state = uploads.computeIfAbsent(key) {
      UploadState(buildId = chunk.buildId, transferId = chunk.transferId, lastSequence = -1L)
    }

    if (state.buildId != chunk.buildId || state.transferId != chunk.transferId) {
      return TransferRejectReason.TRANSFER_REJECT_REASON_MISSING_IDENTITY
    }

    if (chunk.sequence < 0 || chunk.sequence <= state.lastSequence) {
      return TransferRejectReason.TRANSFER_REJECT_REASON_SEQUENCE_VIOLATION
    }

    state.lastSequence = chunk.sequence
    return TransferRejectReason.TRANSFER_REJECT_REASON_NONE
  }

  private fun key(buildId: String, transferId: String): String = "$buildId::$transferId"

  private data class UploadState(
    val buildId: String,
    val transferId: String,
    @Volatile var lastSequence: Long,
  )
}
