package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.TransferRejectReason
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BuildSessionGrpcServiceTest {

  @Test
  fun `publishDataStream returns sequence violation reason`() = runBlocking {
    val service = BuildSessionGrpcService(module = NoopModule())
    val payload = "hello".encodeToByteArray()

    val ack = service.publishDataStream(
      flowOf(
        chunk(seq = 1, payload = payload),
        chunk(seq = 1, payload = payload),
      ),
    )

    assertFalse(ack.accepted)
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_SEQUENCE_VIOLATION, ack.rejectReason)
  }

  @Test
  fun `publishDataStream returns checksum mismatch reason`() = runBlocking {
    val service = BuildSessionGrpcService(module = NoopModule())
    val ack = service.publishDataStream(
      flowOf(
        DataChunk.newBuilder()
          .setBuildId("build-1")
          .setTransferId("tx-1")
          .setSequence(1)
          .setPayload(ByteString.copyFromUtf8("bad"))
          .setChecksum(ByteString.copyFromUtf8("not-sha256"))
          .build(),
      ),
    )

    assertFalse(ack.accepted)
    assertEquals(TransferRejectReason.TRANSFER_REJECT_REASON_CHECKSUM_MISMATCH, ack.rejectReason)
  }

  private fun chunk(seq: Long, payload: ByteArray): DataChunk = DataChunk.newBuilder()
    .setBuildId("build-1")
    .setTransferId("tx-1")
    .setSequence(seq)
    .setPayload(ByteString.copyFrom(payload))
    .setChecksum(BuildDataStreamStore.checksumFor(payload))
    .build()
}

private class NoopModule : BuildGrpcModule {
  override suspend fun initialize(request: BuildInit): BuildServerInfo =
    BuildServerInfo("noop", "1", emptyList(), emptyList())

  override fun startBuild(request: BuildStart) = kotlinx.coroutines.flow.emptyFlow()

  override suspend fun shutdown(reason: String): Boolean = true
}
