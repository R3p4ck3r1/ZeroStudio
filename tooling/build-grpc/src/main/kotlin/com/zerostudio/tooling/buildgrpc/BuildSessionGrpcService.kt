package com.zerostudio.tooling.buildgrpc

import com.google.protobuf.ByteString
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import com.zerostudio.tooling.buildgrpc.proto.BuildSessionServiceGrpcKt
import com.zerostudio.tooling.buildgrpc.proto.ExecuteActionRequest
import com.zerostudio.tooling.buildgrpc.proto.ExecuteActionResponse
import com.zerostudio.tooling.buildgrpc.proto.InitializeRequest
import com.zerostudio.tooling.buildgrpc.proto.NegotiateContextRequest
import com.zerostudio.tooling.buildgrpc.proto.NegotiateContextResponse
import com.zerostudio.tooling.buildgrpc.proto.ResourceChunk
import com.zerostudio.tooling.buildgrpc.proto.ResourceTransferAck
import com.zerostudio.tooling.buildgrpc.proto.ResourceTransferRequest
import com.zerostudio.tooling.buildgrpc.proto.SerializationCodec
import com.zerostudio.tooling.buildgrpc.proto.TransferCompression
import com.zerostudio.tooling.buildgrpc.proto.InitializeResponse
import com.zerostudio.tooling.buildgrpc.proto.ShutdownRequest
import com.zerostudio.tooling.buildgrpc.proto.ShutdownResponse
import com.zerostudio.tooling.buildgrpc.proto.StartBuildRequest
import com.zerostudio.tooling.buildgrpc.proto.StreamBuildEventsRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
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



  override suspend fun negotiateContext(request: NegotiateContextRequest): NegotiateContextResponse {
    val accepted = request.requestedCapabilitiesList
      .filter { capability -> capability in module.supportedFeatures() }

    val codec = if (request.preferredCodec != SerializationCodec.SERIALIZATION_CODEC_UNSPECIFIED) {
      request.preferredCodec
    } else {
      SerializationCodec.SERIALIZATION_CODEC_PROTOBUF
    }

    val compression = if (
      request.preferredCompression != TransferCompression.TRANSFER_COMPRESSION_UNSPECIFIED
    ) {
      request.preferredCompression
    } else {
      TransferCompression.TRANSFER_COMPRESSION_ZSTD
    }

    return NegotiateContextResponse.newBuilder()
      .addAllAcceptedCapabilities(accepted)
      .setNegotiatedCodec(codec)
      .setNegotiatedCompression(compression)
      .setMaxChunkSizeBytes(1024 * 1024)
      .build()
  }

  override suspend fun uploadResource(requests: Flow<ResourceChunk>): ResourceTransferAck {
    var transferId = ""
    var count = 0L
    requests.collect { chunk ->
      transferId = chunk.transferId
      count++
    }

    return ResourceTransferAck.newBuilder()
      .setTransferId(transferId)
      .setAccepted(true)
      .setReceivedChunks(count)
      .setMessage("Resource stream received by stub transport")
      .build()
  }

  override fun downloadResource(request: ResourceTransferRequest): Flow<ResourceChunk> = flow {
    emit(
      ResourceChunk.newBuilder()
        .setTransferId(request.transferId.ifBlank { request.resourceUri })
        .setSequence(0)
        .setEof(true)
        .build(),
    )
  }

  override suspend fun shutdown(request: ShutdownRequest): ShutdownResponse {
    val accepted = module.shutdown(request.reason)
    if (accepted) {
      eventStore.clearAll()
    }
    return ShutdownResponse.newBuilder().setAccepted(accepted).build()
  }
}
