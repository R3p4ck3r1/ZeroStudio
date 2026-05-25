package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.CompressionKind
import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class BuildDataStreamStoreTest {

  @Test
  fun `append and read plain text bytes`() {
    val store = BuildDataStreamStore()
    val payload = "hello-build-protocol".encodeToByteArray()
    val chunk = DataChunk.newBuilder()
      .setBuildId("build-1")
      .setTransferId("tx-plain")
      .setPayload(ByteString.copyFrom(payload))
      .setChecksum(BuildDataStreamStore.checksumFor(payload))
      .build()

    val accepted = store.append(chunk)
    val readBack = store.read("tx-plain", 0, 0)

    assertEquals(payload.size, accepted)
    assertContentEquals(payload, readBack)
  }

  @Test
  fun `append rejects invalid checksum`() {
    val store = BuildDataStreamStore()
    val payload = "invalid-checksum".encodeToByteArray()
    val chunk = DataChunk.newBuilder()
      .setBuildId("build-1")
      .setTransferId("tx-invalid")
      .setPayload(ByteString.copyFrom(payload))
      .setChecksum(ByteString.copyFromUtf8("not-a-real-checksum"))
      .build()

    val accepted = store.append(chunk)
    val readBack = store.read("tx-invalid", 0, 0)

    assertEquals(0, accepted)
    assertContentEquals(ByteArray(0), readBack)
  }

  @Test
  fun `append accepts gzip compressed text`() {
    val store = BuildDataStreamStore()
    val original = "gzip-text-payload".repeat(200).encodeToByteArray()
    val compressed = gzip(original)
    val chunk = DataChunk.newBuilder()
      .setBuildId("build-1")
      .setTransferId("tx-gzip")
      .setCompression(CompressionKind.COMPRESSION_KIND_GZIP)
      .setPayload(ByteString.copyFrom(compressed))
      .setChecksum(BuildDataStreamStore.checksumFor(original))
      .build()

    val accepted = store.append(chunk)
    val readBack = store.read("tx-gzip", 0, 0)

    assertEquals(original.size, accepted)
    assertContentEquals(original, readBack)
  }

  private fun gzip(bytes: ByteArray): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { it.write(bytes) }
    return output.toByteArray()
  }
}
