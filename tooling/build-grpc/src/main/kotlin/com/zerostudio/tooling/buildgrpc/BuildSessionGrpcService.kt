package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import com.zerostudio.tooling.buildgrpc.proto.BuildEventKind
import com.zerostudio.tooling.buildgrpc.proto.ArtifactKind
import com.zerostudio.tooling.buildgrpc.proto.DataTransferEvent
import com.zerostudio.tooling.buildgrpc.proto.ContextFrame
import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.DataTransferAck
import com.zerostudio.tooling.buildgrpc.proto.CompressionKind
import com.zerostudio.tooling.buildgrpc.proto.FetchDataRequest
import com.zerostudio.tooling.buildgrpc.proto.BuildSessionServiceGrpcKt
import com.zerostudio.tooling.buildgrpc.proto.ExecuteActionRequest
import com.zerostudio.tooling.buildgrpc.proto.ExecuteActionResponse
import com.zerostudio.tooling.buildgrpc.proto.InitializeRequest
import com.zerostudio.tooling.buildgrpc.proto.InitializeResponse
import com.zerostudio.tooling.buildgrpc.proto.ShutdownRequest
import com.zerostudio.tooling.buildgrpc.proto.ShutdownResponse
import com.zerostudio.tooling.buildgrpc.proto.StartBuildRequest
import com.zerostudio.tooling.buildgrpc.proto.StreamBuildEventsRequest
import com.zerostudio.tooling.buildgrpc.proto.TransferRejectReason
import com.zerostudio.tooling.buildgrpc.proto.CleanupTransferResponse
import com.zerostudio.tooling.buildgrpc.proto.CleanupTransferRequest
import com.zerostudio.tooling.buildgrpc.proto.QueryTransferCursorResponse
import com.zerostudio.tooling.buildgrpc.proto.QueryTransferCursorRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * gRPC service implementation for BuildSessionService.
 */
private data class TransferStats(
  val buildId: String,
  val transferId: String,
  val contentType: String,
  val compression: CompressionKind,
  val transferredBytes: Long,
  val chunkCount: Long,
  val durationMillis: Long,
  val accepted: Boolean,
  val rejectReason: TransferRejectReason = TransferRejectReason.TRANSFER_REJECT_REASON_NONE,
)

class BuildSessionGrpcService(
  private val module: BuildGrpcModule,
  private val actionExecutor: BuildActionExecutor = LocalNoopBuildActionExecutor(),
  private val reapiExecutionBridge: ReapiExecutionBridge = NoopReapiExecutionBridge(),
  private val eventStore: BuildEventStore = BuildEventStore(),
  private val dataStreamStore: BuildDataStreamStore = BuildDataStreamStore(),
  private val contextStateStore: BuildContextStateStore = BuildContextStateStore(),
  private val transferRegistry: BuildTransferRegistry = BuildTransferRegistry(),
) : BuildSessionServiceGrpcKt.BuildSessionServiceCoroutineImplBase() {
  private val buildSequences = ConcurrentHashMap<String, AtomicLong>()
  private val policyMutex = Mutex()
  @Volatile private var runtimePolicy = RuntimeTransportPolicy(
    maxFrameBytes = 64 * 1024,
    compression = CompressionKind.COMPRESSION_KIND_NONE,
    serialization = com.zerostudio.tooling.buildgrpc.proto.SerializationKind.SERIALIZATION_KIND_PROTOBUF_BINARY,
  )

  override suspend fun initialize(request: InitializeRequest): InitializeResponse {
    policyMutex.withLock {
      runtimePolicy = BuildTransportPolicy.negotiate(request)
    }
    val init = BuildProtocolMapper.toInit(request)
    val serverInfo = module.initialize(init)
    return BuildProtocolMapper.toInitResponse(serverInfo).toBuilder()
      .setTransport(
        com.zerostudio.tooling.buildgrpc.proto.TransportConfig.newBuilder()
          .setMaxFrameBytes(runtimePolicy.maxFrameBytes)
          .setCompression(runtimePolicy.compression)
          .setMultiplexEnabled(request.transport.supportsMultiplex)
          .build(),
      )
      .setSerialization(
        com.zerostudio.tooling.buildgrpc.proto.SerializationConfig.newBuilder()
          .setSelected(runtimePolicy.serialization)
          .build(),
      )
      .build()
  }

  override fun startBuild(request: StartBuildRequest): Flow<BuildEventEnvelope> {
    val buildStart = BuildProtocolMapper.toStart(request)
    return module.startBuild(buildStart).onEach(eventStore::append)
  }

  override fun streamBuildEvents(request: StreamBuildEventsRequest): Flow<BuildEventEnvelope> = flow {
    val history = eventStore.getFromSequence(request.buildId, request.fromSequence)
    history.forEach { emit(it) }
  }

  override suspend fun executeAction(request: ExecuteActionRequest): ExecuteActionResponse {
    val reapiResult =
      if (request.hasReapiActionDigest() && request.reapiActionDigest.hash.isNotBlank()) {
        reapiExecutionBridge.execute(
          ReapiExecuteRequest(
            buildId = request.buildId,
            instanceName = request.instanceName,
            actionDigest = request.reapiActionDigest,
            platform = if (request.hasPlatform()) request.platform else null,
            priority = request.priority,
          ),
        )
      } else {
        null
      }

    if (reapiResult != null) {
      return ExecuteActionResponse.newBuilder()
        .setOperationName(reapiResult.operationName)
        .setStatus(reapiResult.status)
        .setActionResult(ByteString.copyFrom(reapiResult.actionResult))
        .build()
    }

    val actionRequest = ActionExecutionRequest(
      buildId = request.buildId,
      actionDigest = request.actionDigest,
      command = request.command.toByteArray(),
      inputRootDigest = request.inputRootDigest.toByteArray(),
    )

    val result = when (module) {
      is RoutingBuildGrpcModule -> module.executeAction(actionRequest)
      else -> actionExecutor.execute(actionRequest)
    }

    return ExecuteActionResponse.newBuilder()
      .setOperationName(result.operationName)
      .setStatus(result.status)
      .setActionResult(ByteString.copyFrom(result.actionResult))
      .build()
  }


  override suspend fun publishDataStream(requests: Flow<DataChunk>): DataTransferAck {
    var buildId = ""
    var transferId = ""
    var receivedBytes = 0L
    var accepted = true
    var rejectReason = TransferRejectReason.TRANSFER_REJECT_REASON_NONE
    var chunkCount = 0L
    var nextExpectedSequence = 0L
    var contentType = ""
    var compression = CompressionKind.COMPRESSION_KIND_UNSPECIFIED
    val startedAt = System.currentTimeMillis()

    requests.collect { chunk ->
      buildId = if (chunk.buildId.isNotBlank()) chunk.buildId else buildId
      transferId = if (chunk.transferId.isNotBlank()) chunk.transferId else transferId
      contentType = if (chunk.contentType.isNotBlank()) chunk.contentType else contentType
      if (chunk.compression != CompressionKind.COMPRESSION_KIND_UNSPECIFIED) {
        compression = chunk.compression
      }

      val validation = transferRegistry.validateUploadChunk(chunk)
      nextExpectedSequence = validation.nextExpectedSequence
      val appendResult = if (validation.reason == TransferRejectReason.TRANSFER_REJECT_REASON_NONE) {
        dataStreamStore.append(chunk)
      } else {
        BuildDataStreamStore.AppendResult(0, validation.reason)
      }
      if (appendResult.acceptedBytes == 0 && chunk.payload.size() > 0) {
        accepted = false
        if (rejectReason == TransferRejectReason.TRANSFER_REJECT_REASON_NONE) {
          rejectReason = appendResult.reason
        }
      }

      receivedBytes += appendResult.acceptedBytes
      chunkCount += 1
    }

    appendTransferEvent(
      stats = TransferStats(
        buildId = buildId,
        transferId = transferId,
        contentType = contentType,
        compression = compression,
        transferredBytes = receivedBytes,
        chunkCount = chunkCount,
        durationMillis = System.currentTimeMillis() - startedAt,
        accepted = accepted,
        rejectReason = rejectReason,
      ),
      totalBytes = receivedBytes,
    )

    return DataTransferAck.newBuilder()
      .setTransferId(transferId)
      .setAccepted(accepted)
      .setReceivedBytes(receivedBytes)
      .setMessage(if (accepted) "accepted" else rejectReason.name)
      .setRejectReason(rejectReason)
      .setNextExpectedSequence(nextExpectedSequence)
      .build()
  }

  override fun fetchDataStream(request: FetchDataRequest): Flow<DataChunk> = flow {
    val startedAt = System.currentTimeMillis()
    val bytes = dataStreamStore.read(request.transferId, request.offset, request.maxBytes)
    val chunkSize = runtimePolicy.maxFrameBytes.coerceAtLeast(4 * 1024)
    var cursor = 0
    var sequence = (request.offset / chunkSize).coerceAtLeast(0) + 1
    var emittedChunkCount = 0L

    if (bytes.isEmpty()) {
      emit(
        DataChunk.newBuilder()
           .setBuildId(request.buildId)
          .setTransferId(request.transferId)
          .setSequence(sequence)
          .setEof(true)
          .build(),
      )
      return@flow
    }

    while (cursor < bytes.size) {
      val end = (cursor + chunkSize).coerceAtMost(bytes.size)
      val slice = bytes.copyOfRange(cursor, end)
      val isEof = end == bytes.size
      emit(
        DataChunk.newBuilder()
           .setBuildId(request.buildId)
          .setTransferId(request.transferId)
          .setSequence(sequence)
          .setPayload(ByteString.copyFrom(slice))
          .setChecksum(BuildDataStreamStore.checksumFor(slice))
          .setEof(isEof)
          .build(),
      )
      cursor = end
      sequence += 1
      emittedChunkCount += 1
    }

    appendTransferEvent(
      stats = TransferStats(
        buildId = request.buildId,
        transferId = request.transferId,
        contentType = "",
        compression = CompressionKind.COMPRESSION_KIND_NONE,
        transferredBytes = bytes.size.toLong(),
        chunkCount = emittedChunkCount,
        durationMillis = System.currentTimeMillis() - startedAt,
        accepted = true,
        rejectReason = TransferRejectReason.TRANSFER_REJECT_REASON_NONE,
      ),
      totalBytes = bytes.size.toLong(),
    )
  }


  override suspend fun queryTransferCursor(request: QueryTransferCursorRequest): QueryTransferCursorResponse {
    val found = transferRegistry.hasTransfer(request.buildId, request.transferId)
    val next = transferRegistry.nextExpectedSequence(request.buildId, request.transferId)
    return QueryTransferCursorResponse.newBuilder()
      .setBuildId(request.buildId)
      .setTransferId(request.transferId)
      .setNextExpectedSequence(next)
      .setFound(found)
      .setCommittedBytes(dataStreamStore.sizeOf(request.transferId))
      .build()
  }


  override suspend fun cleanupTransfer(request: CleanupTransferRequest): CleanupTransferResponse {
    val removedData = dataStreamStore.remove(request.transferId)
    val removedCursor = transferRegistry.removeTransfer(request.buildId, request.transferId)
    return CleanupTransferResponse.newBuilder()
      .setRemoved(removedData || removedCursor)
      .build()
  }

  override fun exchangeContext(requests: Flow<ContextFrame>): Flow<ContextFrame> = flow {
    requests.collect { frame ->
      contextStateStore.update(frame)
      emit(frame)
    }
  }


  private fun appendTransferEvent(
    stats: TransferStats,
    totalBytes: Long = stats.transferredBytes,
  ) {
    if (stats.buildId.isBlank()) {
      return
    }

    val event = BuildEventEnvelope.newBuilder()
      .setBuildId(stats.buildId)
      .setSequence(nextEventSequence(stats.buildId))
      .setTimestampMillis(System.currentTimeMillis())
      .setKind(BuildEventKind.BUILD_EVENT_KIND_DATA_TRANSFER)
      .setTransfer(
        DataTransferEvent.newBuilder()
          .setTransferId(stats.transferId)
          .setTotalBytes(totalBytes)
          .setTransferredBytes(stats.transferredBytes)
          .setChunkCount(stats.chunkCount)
          .setDurationMillis(stats.durationMillis)
          .setAccepted(stats.accepted)
          .setRejectReason(stats.rejectReason)
          .setCompression(stats.compression)
          .setArtifactKind(inferArtifactKind(stats.transferId, stats.contentType))
          .build(),
      )
      .build()

    eventStore.append(event)
  }

  private fun nextEventSequence(buildId: String): Long =
    buildSequences.computeIfAbsent(buildId) { AtomicLong(0L) }.incrementAndGet()

  private fun inferArtifactKind(transferId: String, contentType: String): ArtifactKind {
    val id = transferId.lowercase()
    val type = contentType.lowercase()
    return when {
      id.endsWith(".jar") -> ArtifactKind.ARTIFACT_KIND_JAR
      id.endsWith(".aar") -> ArtifactKind.ARTIFACT_KIND_AAR
      id.endsWith(".zip") -> ArtifactKind.ARTIFACT_KIND_ZIP
      id.endsWith(".tar.gz") || id.endsWith(".tgz") -> ArtifactKind.ARTIFACT_KIND_TAR_GZ
      id.endsWith(".desc") || id.endsWith(".pb") -> ArtifactKind.ARTIFACT_KIND_PROTO_DESCRIPTOR
      id.endsWith(".log") || type.contains("text/plain") -> ArtifactKind.ARTIFACT_KIND_BUILD_LOG
      id.endsWith(".kt") || id.endsWith(".java") || id.endsWith(".scala") || id.endsWith(".cpp") ||
        id.endsWith(".c") || id.endsWith(".h") || id.endsWith(".py") || id.endsWith(".rs") ||
        type.contains("text/") -> ArtifactKind.ARTIFACT_KIND_SOURCE_TEXT
      type.contains("application/vnd.build.remote-cache") || id.contains("/cas/") ->
        ArtifactKind.ARTIFACT_KIND_REMOTE_CACHE_BLOB
      else -> ArtifactKind.ARTIFACT_KIND_UNSPECIFIED
    }
  }

  override suspend fun shutdown(request: ShutdownRequest): ShutdownResponse {
    val accepted = module.shutdown(request.reason)
    if (accepted) {
      eventStore.clearAll()
    }
    return ShutdownResponse.newBuilder().setAccepted(accepted).build()
  }
}
