package com.zerostudio.tooling.buildgrpc

/**
 * 协议层领域模型：对齐 BSP 初始化/能力协商 + BEP 事件流语义。
 */
data class BuildClientMetadata(
  val clientName: String,
  val clientVersion: String,
  val capabilities: Set<String> = emptySet(),
)

data class BuildServerMetadata(
  val serverName: String,
  val serverVersion: String,
  val supportedLanguages: Set<String>,
  val protocolFeatures: Set<String>,
)

enum class BuildLifecycleState {
  CREATED,
  INITIALIZED,
  RUNNING,
  FINISHED,
  FAILED,
  CANCELLED,
}

data class BuildTargetDescriptor(
  val label: String,
  val language: String? = null,
)

data class BuildExecutionPlan(
  val buildId: String,
  val targets: List<BuildTargetDescriptor>,
  val options: Map<String, String> = emptyMap(),
)

enum class SerializationCodec {
  CODEC_UNSPECIFIED,
  PROTOBUF,
  FLATBUFFERS,
  CAPN_PROTO,
}

enum class TransferCompression {
  COMPRESSION_UNSPECIFIED,
  NONE,
  ZSTD,
  LZ4,
  GZIP,
}

data class ProtocolContext(
  val workspaceRoot: String,
  val buildSystemId: String,
  val buildSystemVersion: String,
  val executionEnvironment: Map<String, String> = emptyMap(),
  val requestedCapabilities: Set<String> = emptySet(),
)

data class ResourceTransferDescriptor(
  val transferId: String,
  val resourceUri: String,
  val totalBytes: Long,
  val digest: String,
  val codec: SerializationCodec,
  val compression: TransferCompression,
  val chunkSizeBytes: Int,
)

data class ResourceChunk(
  val transferId: String,
  val sequence: Long,
  val payload: ByteArray,
  val eof: Boolean,
)

data class SourceTextPayload(
  val workspaceRoot: String,
  val relativePath: String,
  val languageId: String,
  val charset: String = "UTF-8",
  val text: String,
  val version: Long = 0L,
  val checksum: ByteArray = ByteArray(0),
)

data class FileObjectPayload(
  val workspaceRoot: String,
  val relativePath: String,
  val contentType: String,
  val sizeBytes: Long,
  val inlineContent: ByteArray = ByteArray(0),
  val transferId: String? = null,
  val checksum: ByteArray = ByteArray(0),
  val modifiedAtEpochMillis: Long? = null,
)

data class GradleTaskPayload(
  val path: String,
  val displayName: String = path,
  val arguments: List<String> = emptyList(),
  val jvmArguments: List<String> = emptyList(),
  val environment: Map<String, String> = emptyMap(),
  val selected: Boolean = true,
)

data class BuildLogOptionsPayload(
  val streamStdout: Boolean = true,
  val streamStderr: Boolean = true,
  val includeGradleLifecycle: Boolean = true,
  val includeStacktraces: Boolean = true,
  val maxBufferedLines: Int = 2_000,
)

data class BuildLogLinePayload(
  val buildId: String,
  val sequence: Long,
  val timestampEpochMillis: Long,
  val logger: String,
  val level: String,
  val message: String,
  val throwable: String? = null,
)
