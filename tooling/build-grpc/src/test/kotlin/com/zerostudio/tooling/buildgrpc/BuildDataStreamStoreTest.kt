package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.github.luben.zstd.Zstd
import com.zerostudio.tooling.buildgrpc.proto.CompressionKind
import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import net.jpountz.lz4.LZ4Factory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import java.io.ByteArrayOutputStream
import java.nio.file.Files
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

    assertEquals(payload.size, accepted.acceptedBytes)
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

    assertEquals(0, accepted.acceptedBytes)
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

    assertEquals(original.size, accepted.acceptedBytes)
    assertContentEquals(original, readBack)
  }

  @Test
  fun `append accepts zstd compressed text`() {
    val store = BuildDataStreamStore()
    val original = "zstd-text-payload".repeat(200).encodeToByteArray()
    val compressed = Zstd.compress(original)
    val chunk = DataChunk.newBuilder()
      .setBuildId("build-1")
      .setTransferId("tx-zstd")
      .setCompression(CompressionKind.COMPRESSION_KIND_ZSTD)
      .setPayload(ByteString.copyFrom(compressed))
      .setChecksum(BuildDataStreamStore.checksumFor(original))
      .build()

    val accepted = store.append(chunk)
    val readBack = store.read("tx-zstd", 0, 0)

    assertEquals(original.size, accepted.acceptedBytes)
    assertContentEquals(original, readBack)
  }

  @Test
  fun `append accepts lz4 compressed text`() {
    val store = BuildDataStreamStore()
    val original = "lz4-text-payload".repeat(200).encodeToByteArray()
    val compressed = lz4WithLengthPrefix(original)
    val chunk = DataChunk.newBuilder()
      .setBuildId("build-1")
      .setTransferId("tx-lz4")
      .setCompression(CompressionKind.COMPRESSION_KIND_LZ4)
      .setPayload(ByteString.copyFrom(compressed))
      .setChecksum(BuildDataStreamStore.checksumFor(original))
      .build()

    val accepted = store.append(chunk)
    val readBack = store.read("tx-lz4", 0, 0)

    assertEquals(original.size, accepted.acceptedBytes)
    assertContentEquals(original, readBack)
  }


  @Test
  fun `persists payload on disk across store instances`() {
    val dir = Files.createTempDirectory("build-grpc-store-test")
    val writer = BuildDataStreamStore(baseDir = dir)
    val payload = "persist-me".repeat(100).encodeToByteArray()
    val chunk = DataChunk.newBuilder()
      .setBuildId("build-1")
      .setTransferId("tx-persist")
      .setPayload(ByteString.copyFrom(payload))
      .setChecksum(BuildDataStreamStore.checksumFor(payload))
      .build()

    writer.append(chunk)

    val reader = BuildDataStreamStore(baseDir = dir)
    val readBack = reader.read("tx-persist", 0, 0)
    assertContentEquals(payload, readBack)
  }

  private fun gzip(bytes: ByteArray): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { it.write(bytes) }
    return output.toByteArray()
  }

  private fun lz4WithLengthPrefix(bytes: ByteArray): ByteArray {
    val compressor = LZ4Factory.fastestInstance().fastCompressor()
    val max = compressor.maxCompressedLength(bytes.size)
    val compressed = ByteArray(max)
    val written = compressor.compress(bytes, 0, bytes.size, compressed, 0, max)
    val result = ByteArray(Int.SIZE_BYTES + written)
    java.nio.ByteBuffer.wrap(result, 0, Int.SIZE_BYTES).putInt(bytes.size)
    compressed.copyInto(result, Int.SIZE_BYTES, 0, written)
    return result
  }
}
