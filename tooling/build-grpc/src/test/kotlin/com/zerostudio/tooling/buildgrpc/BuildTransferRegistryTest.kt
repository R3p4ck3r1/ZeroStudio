package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.TransferRejectReason
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildTransferRegistryTest {
  @Test
  fun `accepts strictly increasing chunk sequence`() {
    val registry = BuildTransferRegistry()
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_NONE, registry.validateUploadChunk(chunk(1)))
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_NONE, registry.validateUploadChunk(chunk(2)))
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_NONE, registry.validateUploadChunk(chunk(3)))
  }

  @Test
  fun `rejects duplicate or decreasing sequence`() {
    val registry = BuildTransferRegistry()
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_NONE, registry.validateUploadChunk(chunk(2)))
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_SEQUENCE_VIOLATION, registry.validateUploadChunk(chunk(2)))
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_SEQUENCE_VIOLATION, registry.validateUploadChunk(chunk(1)))
  }

  @Test
  fun `rejects missing identity`() {
    val registry = BuildTransferRegistry()
    val missingBuild = DataChunk.newBuilder().setTransferId("t").setSequence(1).build()
    val missingTransfer = DataChunk.newBuilder().setBuildId("b").setSequence(1).build()
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_MISSING_IDENTITY, registry.validateUploadChunk(missingBuild))
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_MISSING_IDENTITY, registry.validateUploadChunk(missingTransfer))
  }

  private fun chunk(seq: Long): DataChunk = DataChunk.newBuilder()
    .setBuildId("build-1")
    .setTransferId("transfer-1")
    .setSequence(seq)
    .build()
}
