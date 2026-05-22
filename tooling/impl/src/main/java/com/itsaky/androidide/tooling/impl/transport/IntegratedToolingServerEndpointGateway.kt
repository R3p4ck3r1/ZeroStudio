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
import java.util.concurrent.CompletableFuture

/**
 * Transitional endpoint for the integrated AIDL+gRPC+REAPI stack.
 *
 * Iteration2 stage: this gateway delegates to legacy JSON-RPC endpoint implementation while
 * preserving an isolated integration seam for the future stack cutover.
 */
class IntegratedToolingServerEndpointGateway(delegate: IToolingApiServer) :
    ToolingTransportServerEndpoint {

  private val legacyEndpoint = LegacyToolingServerEndpoint(delegate)

  override fun metadata(): CompletableFuture<ToolingServerMetadata> = legacyEndpoint.metadata()

  override fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult> =
      legacyEndpoint.initialize(params)

  override fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult> =
      legacyEndpoint.executeTasks(message)

  override fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult> =
      legacyEndpoint.execute(request)

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> =
      legacyEndpoint.cancelCurrentBuild()

  override fun shutdown(): CompletableFuture<Void> = legacyEndpoint.shutdown()
}
