package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildTransferRegistryTest {
  @Test
  fun `accepts strictly increasing chunk sequence`() {
    val registry = BuildTransferRegistry()
    assertTrue(registry.validateUploadChunk(chunk(1)))
    assertTrue(registry.validateUploadChunk(chunk(2)))
    assertTrue(registry.validateUploadChunk(chunk(3)))
  }

  @Test
  fun `rejects duplicate or decreasing sequence`() {
    val registry = BuildTransferRegistry()
    assertTrue(registry.validateUploadChunk(chunk(2)))
    assertFalse(registry.validateUploadChunk(chunk(2)))
    assertFalse(registry.validateUploadChunk(chunk(1)))
  }

  @Test
  fun `rejects missing identity`() {
    val registry = BuildTransferRegistry()
    val missingBuild = DataChunk.newBuilder().setTransferId("t").setSequence(1).build()
    val missingTransfer = DataChunk.newBuilder().setBuildId("b").setSequence(1).build()
    assertFalse(registry.validateUploadChunk(missingBuild))
    assertFalse(registry.validateUploadChunk(missingTransfer))
  }

  private fun chunk(seq: Long): DataChunk = DataChunk.newBuilder()
    .setBuildId("build-1")
    .setTransferId("transfer-1")
    .setSequence(seq)
    .build()
}
