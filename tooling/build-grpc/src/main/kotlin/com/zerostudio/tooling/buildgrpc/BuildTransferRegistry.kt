package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks transfer-level identity and sequencing constraints.
 */
class BuildTransferRegistry {
  private val uploads = ConcurrentHashMap<String, UploadState>()

  fun validateUploadChunk(chunk: DataChunk): Boolean {
    if (chunk.buildId.isBlank() || chunk.transferId.isBlank()) {
      return false
    }

    val key = key(chunk.buildId, chunk.transferId)
    val state = uploads.computeIfAbsent(key) {
      UploadState(buildId = chunk.buildId, transferId = chunk.transferId, lastSequence = -1L)
    }

    if (state.buildId != chunk.buildId || state.transferId != chunk.transferId) {
      return false
    }

    if (chunk.sequence < 0 || chunk.sequence <= state.lastSequence) {
      return false
    }

    state.lastSequence = chunk.sequence
    return true
  }

  private fun key(buildId: String, transferId: String): String = "$buildId::$transferId"

  private data class UploadState(
    val buildId: String,
    val transferId: String,
    @Volatile var lastSequence: Long,
  )
}
