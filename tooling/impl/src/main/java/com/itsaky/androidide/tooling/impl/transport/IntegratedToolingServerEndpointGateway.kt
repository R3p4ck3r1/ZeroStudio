package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import com.itsaky.androidide.tooling.api.transport.ToolingTransportServerEndpoint
import com.itsaky.androidide.tooling.impl.transport.integrated.IntegratedProtocolCoordinator
import com.itsaky.androidide.tooling.impl.transport.integrated.IntegratedBuildRequestCodec
import com.itsaky.androidide.tooling.impl.transport.integrated.IntegratedBinaryRuntimeBridge
import com.itsaky.androidide.tooling.impl.transport.reapi.GrpcReapiExecutionGateway
import com.itsaky.androidide.tooling.impl.transport.reapi.NoOpReapiExecutionGateway
import com.itsaky.androidide.tooling.impl.transport.reapi.ReapiExecutionGateway
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory

/**
 * Transitional endpoint for the integrated AIDL+gRPC+REAPI stack.
 *
 * Iteration2 stage: this gateway delegates to legacy JSON-RPC endpoint implementation while
 * preserving an isolated integration seam for the future stack cutover.
 */
class IntegratedToolingServerEndpointGateway(private val delegate: IToolingApiServer) :
    ToolingTransportServerEndpoint {

  private val log = LoggerFactory.getLogger(IntegratedToolingServerEndpointGateway::class.java)
  private val runtimeConfig = IntegratedTransportRuntimeConfig.fromSystemProperties()
  private val protocolCoordinator = IntegratedProtocolCoordinator(runtimeConfig)
  private val reapiGateway: ReapiExecutionGateway =
      if (runtimeConfig.reapiEnabled && runtimeConfig.reapiEndpoint.isNotBlank()) {
        GrpcReapiExecutionGateway(runtimeConfig.reapiEndpoint, runtimeConfig.reapiInstanceName)
      } else {
        NoOpReapiExecutionGateway()
      }

  init {
    protocolCoordinator.startHandshake().whenComplete { handshake, error ->
      if (error != null) {
        log.error("Integrated handshake bootstrap failed", error)
      } else {
        log.info(
            "Integrated handshake established. version={}, sessionId={}, aidl={}, grpcUds={}, reapi={}",
            handshake.protocolVersion,
            handshake.sessionId,
            handshake.supportsAidlControlPlane,
            handshake.supportsGrpcUdsDataPlane,
            handshake.supportsReapiExecution,
        )
      }
    }

    log.info(
        "Integrated transport gateway booted. reapiEnabled={}, reapiEndpoint='{}', reapiInstance='{}'",
        reapiGateway.isEnabled(),
        runtimeConfig.reapiEndpoint,
        runtimeConfig.reapiInstanceName,
    )
  }

  override fun metadata(): CompletableFuture<ToolingServerMetadata> = delegate.metadata()

  override fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult> {
    emitRuntimePayload("initialize", IntegratedBuildRequestCodec.encodeInitialize(params)) { bytes ->
      log.debug(
          "Integrated initialize mirrored to binary payload: requestId={}, bytes={}",
          params.requestId,
          bytes.size,
      )
    }
    return delegate.initialize(params)
  }

  override fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult> {
    emitRuntimePayload(
        "submitBuildRequest",
        IntegratedBuildRequestCodec.encodeTaskExecution(message),
    ) { bytes ->
      log.debug(
          "Integrated executeTasks mirrored to binary payload: tasks={}, bytes={}",
          message.tasks.size,
          bytes.size,
      )
    }
    return delegate.executeTasks(message)
  }

  override fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult> {
    emitRuntimePayload(
        "submitBuildRequest",
        IntegratedBuildRequestCodec.encodeExecution(request),
    ) { bytes ->
      log.debug(
          "Integrated execute mirrored to binary payload: requestId={}, tasks={}, bytes={}",
          request.requestId,
          request.tasks.size,
          bytes.size,
      )
    }
    return delegate.execute(request)
  }

  private fun emitRuntimePayload(
      methodName: String,
      payload: ByteArray,
      logPayload: (ByteArray) -> Unit,
  ) {
    try {
      logPayload(payload)
      val runtime = IntegratedBinaryRuntimeBridge.getOrCreate()
      runtime.javaClass.getMethod(methodName, ByteArray::class.java).invoke(runtime, payload)
    } catch (error: Throwable) {
      log.warn(
          "Integrated binary runtime mirror failed for '{}'. Continuing through legacy Tooling API delegate.",
          methodName,
          error,
      )
    }
  }

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> =
      delegate.cancelCurrentBuild()

  override fun shutdown(): CompletableFuture<Void> {
    reapiGateway.shutdown()
    val delegateShutdown = delegate.shutdown()
    val protocolShutdown = protocolCoordinator.shutdown()
    return CompletableFuture.allOf(delegateShutdown, protocolShutdown)
  }
}
