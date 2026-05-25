package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
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
    val result = actionExecutor.execute(
      ActionExecutionRequest(
        buildId = request.buildId,
        actionDigest = request.actionDigest,
        command = request.command.toByteArray(),
        inputRootDigest = request.inputRootDigest.toByteArray(),
      ),
    )

    return ExecuteActionResponse.newBuilder()
      .setOperationName(result.operationName)
      .setStatus(result.status)
      .setActionResult(ByteString.copyFrom(result.actionResult))
      .build()
  }

  override suspend fun shutdown(request: ShutdownRequest): ShutdownResponse {
    val accepted = module.shutdown(request.reason)
    if (accepted) {
      eventStore.clearAll()
    }
    return ShutdownResponse.newBuilder().setAccepted(accepted).build()
  }
}
