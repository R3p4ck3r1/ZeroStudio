package com.zerostudio.tooling.buildgrpc.customapi

import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import java.util.concurrent.CompletableFuture

/**
 * Adapter exposing the legacy [IToolingApiServer] contract over the build-grpc binary protocol.
 *
 * This allows incremental migration of callers from JSON-RPC wiring to binary RPC transport
 * without changing higher-level tooling API interfaces.
 */
class ToolingApiBinaryServerAdapter(
  private val facade: ToolingApiBinaryFacade,
  private val rootProjectProvider: () -> com.itsaky.androidide.tooling.api.IProject,
) : IToolingApiServer {

  override fun metadata(): CompletableFuture<ToolingServerMetadata> = facade.metadata()

  override fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult> =
    facade.initialize(params)

  override fun isServerInitialized(): CompletableFuture<Boolean> =
    CompletableFuture.completedFuture(true)

  override fun getRootProject(): CompletableFuture<com.itsaky.androidide.tooling.api.IProject> =
    CompletableFuture.completedFuture(rootProjectProvider())

  override fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult> =
    facade.executeTasks(message)

  override fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult> =
    facade.execute(request)

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> =
    facade.cancelCurrentBuild()

  override fun shutdown(): CompletableFuture<Void> = facade.shutdown()
}
