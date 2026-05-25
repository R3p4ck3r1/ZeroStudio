package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import com.zerostudio.tooling.buildgrpc.proto.BuildEventKind
import com.zerostudio.tooling.buildgrpc.proto.DataTransferEvent
import com.zerostudio.tooling.buildgrpc.proto.ContextFrame
import com.zerostudio.tooling.buildgrpc.proto.DataChunk
import com.zerostudio.tooling.buildgrpc.proto.DataTransferAck
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

/**
 * gRPC service implementation for BuildSessionService.
 */
private data class TransferStats(
  val buildId: String,
  val transferId: String,
  val transferredBytes: Long,
  val chunkCount: Long,
  val durationMillis: Long,
  val accepted: Boolean,
  val rejectReason: TransferRejectReason = TransferRejectReason.TRANSFER_REJECT_REASON_NONE,
)

class BuildSessionGrpcService(
  private val module: BuildGrpcModule,
  private val actionExecutor: BuildActionExecutor = LocalNoopBuildActionExecutor(),
  private val eventStore: BuildEventStore = BuildEventStore(),
  private val dataStreamStore: BuildDataStreamStore = BuildDataStreamStore(),
  private val contextStateStore: BuildContextStateStore = BuildContextStateStore(),
  private val transferRegistry: BuildTransferRegistry = BuildTransferRegistry(),
) : BuildSessionServiceGrpcKt.BuildSessionServiceCoroutineImplBase() {

  override suspend fun initialize(request: InitializeRequest): InitializeResponse {
    val init = BuildProtocolMapper.toInit(request)
    val serverInfo = module.initialize(init)
    return BuildProtocolMapper.toInitResponse(serverInfo)
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
    val startedAt = System.currentTimeMillis()

    requests.collect { chunk ->
      buildId = if (chunk.buildId.isNotBlank()) chunk.buildId else buildId
      transferId = if (chunk.transferId.isNotBlank()) chunk.transferId else transferId

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
    val chunkSize = 64 * 1024
    var cursor = 0
    var sequence = 0L

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
    }

    appendTransferEvent(
      stats = TransferStats(
        buildId = request.buildId,
        transferId = request.transferId,
        transferredBytes = bytes.size.toLong(),
        chunkCount = sequence,
        durationMillis = System.currentTimeMillis() - startedAt,
        accepted = true,
        rejectReason = TransferRejectReason.TRANSFER_REJECT_REASON_NONE,
      ),
      totalBytes = bytes.size.toLong(),
    )
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
      .setSequence(System.nanoTime())
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
          .build(),
      )
      .build()

    eventStore.append(event)
  }

  override suspend fun shutdown(request: ShutdownRequest): ShutdownResponse {
    val accepted = module.shutdown(request.reason)
    if (accepted) {
      eventStore.clearAll()
    }
    return ShutdownResponse.newBuilder().setAccepted(accepted).build()
  }
}
