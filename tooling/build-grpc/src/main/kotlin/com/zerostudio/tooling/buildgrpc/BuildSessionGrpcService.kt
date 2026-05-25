package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flow

/**
 * gRPC service implementation for BuildSessionService.
 */
class BuildSessionGrpcService(
  private val module: BuildGrpcModule,
  private val actionExecutor: BuildActionExecutor = LocalNoopBuildActionExecutor(),
  private val eventStore: BuildEventStore = BuildEventStore(),
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
    var bytes = 0L
    requests.collect { chunk ->
      transferId = if (chunk.transferId.isNotBlank()) chunk.transferId else transferId
      bytes += chunk.payload.size().toLong()
    }

    return DataTransferAck.newBuilder()
      .setTransferId(transferId)
      .setAccepted(true)
      .setReceivedBytes(bytes)
      .setMessage("accepted")
      .build()
  }

  override fun fetchDataStream(request: FetchDataRequest): Flow<DataChunk> = flow {
    emit(
      DataChunk.newBuilder()
        .setTransferId(request.transferId)
        .setSequence(0)
        .setEof(true)
        .build(),
    )
  }

  override fun exchangeContext(requests: Flow<ContextFrame>): Flow<ContextFrame> = requests

  override suspend fun shutdown(request: ShutdownRequest): ShutdownResponse {
    val accepted = module.shutdown(request.reason)
    if (accepted) {
      eventStore.clearAll()
    }
    return ShutdownResponse.newBuilder().setAccepted(accepted).build()
  }
}
