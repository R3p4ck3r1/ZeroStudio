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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

/**
 * gRPC service implementation for BuildSessionService.
 */
class BuildSessionGrpcService(
  private val module: BuildGrpcModule,
  private val actionExecutor: BuildActionExecutor = LocalNoopBuildActionExecutor(),
  private val eventStore: BuildEventStore = BuildEventStore(),
  private val dataStreamStore: BuildDataStreamStore = BuildDataStreamStore(),
  private val contextStateStore: BuildContextStateStore = BuildContextStateStore(),
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
    var transferId = ""
    var receivedBytes = 0L
    var accepted = true

    requests.collect { chunk ->
      transferId = if (chunk.transferId.isNotBlank()) chunk.transferId else transferId
      val acceptedBytes = dataStreamStore.append(chunk)
      if (acceptedBytes == 0 && chunk.payload.size() > 0) {
        accepted = false
      }
      receivedBytes += acceptedBytes
    }

    appendTransferEvent(
      buildId = transferId.substringBefore('#', missingDelimiterValue = ""),
      transferId = transferId,
      transferredBytes = receivedBytes,
    )

    return DataTransferAck.newBuilder()
      .setTransferId(transferId)
      .setAccepted(accepted)
      .setReceivedBytes(receivedBytes)
      .setMessage(if (accepted) "accepted" else "checksum mismatch or invalid transfer id")
      .build()
  }

  override fun fetchDataStream(request: FetchDataRequest): Flow<DataChunk> = flow {
    val bytes = dataStreamStore.read(request.transferId, request.offset, request.maxBytes)
    val chunkSize = 64 * 1024
    var cursor = 0
    var sequence = 0L

    if (bytes.isEmpty()) {
      emit(
        DataChunk.newBuilder()
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
      buildId = request.transferId.substringBefore('#', missingDelimiterValue = ""),
      transferId = request.transferId,
      transferredBytes = bytes.size.toLong(),
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
    buildId: String,
    transferId: String,
    transferredBytes: Long,
    totalBytes: Long = transferredBytes,
  ) {
    if (buildId.isBlank()) {
      return
    }

    val event = BuildEventEnvelope.newBuilder()
      .setBuildId(buildId)
      .setSequence(System.nanoTime())
      .setTimestampMillis(System.currentTimeMillis())
      .setKind(BuildEventKind.BUILD_EVENT_KIND_DATA_TRANSFER)
      .setTransfer(
        DataTransferEvent.newBuilder()
          .setTransferId(transferId)
          .setTotalBytes(totalBytes)
          .setTransferredBytes(transferredBytes)
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
