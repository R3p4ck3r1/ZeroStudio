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
import com.itsaky.androidide.tooling.impl.transport.reapi.GrpcReapiExecutionGateway
import com.itsaky.androidide.tooling.impl.transport.reapi.NoOpReapiExecutionGateway
import com.itsaky.androidide.tooling.impl.transport.reapi.ReapiExecutionGateway
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory

/**
 * Transitional endpoint for the integrated AIDL+gRPC+REAPI stack.
 *
 * This endpoint validates and records build-grpc protobuf payloads, but it no longer starts the
 * incomplete in-process build-grpc runtime or reports synthetic build initialization. Real Gradle
 * initialization/execution must complete through the connected Tooling API server before the IDE is
 * marked initialized.
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
    val payload = IntegratedBuildRequestCodec.encodeInitialize(params)
    log.debug(
        "Integrated initialize encoded as build-grpc payload: requestId={}, bytes={}",
        params.requestId,
        payload.size,
    )
    return delegate.initialize(params)
  }

  override fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult> {
    val payload = IntegratedBuildRequestCodec.encodeTaskExecution(message)
    log.debug(
        "Integrated executeTasks encoded as build-grpc payload: tasks={}, bytes={}",
        message.tasks.size,
        payload.size,
    )
    return delegate.executeTasks(message)
  }

  override fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult> {
    val payload = IntegratedBuildRequestCodec.encodeExecution(request)
    log.debug(
        "Integrated execute encoded as build-grpc payload: requestId={}, tasks={}, bytes={}",
        request.requestId,
        request.tasks.size,
        payload.size,
    )
    return delegate.execute(request)
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
