package com.zerostudio.tooling.buildgrpc.customapi

import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import com.zerostudio.tooling.buildgrpc.BinaryBuildServiceApi
import com.zerostudio.tooling.buildgrpc.BuildCancelRequest
import com.zerostudio.tooling.buildgrpc.BuildInitializeRequest
import com.zerostudio.tooling.buildgrpc.BuildShutdownRequest
import com.zerostudio.tooling.buildgrpc.BuildTargetCompileRequest
import com.zerostudio.tooling.buildgrpc.proto.BuildEventKind
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first

/**
 * Binary facade used to replace lsp4j-rpc server call flows in tooling/api consumers.
 *
 * This facade deliberately refuses to report initialization success unless the build-grpc backend
 * advertises a real Gradle Tooling sync capability. That prevents placeholder/in-process backends
 * from causing the editor to enter an endlessly "initialized" state without a running server.
 */
class ToolingApiBinaryFacade(
  private val binaryApi: BinaryBuildServiceApi,
) {
  private val executor = Executors.newCachedThreadPool()
  fun metadata(): CompletableFuture<ToolingServerMetadata> = CompletableFuture.completedFuture(
    ToolingServerMetadata(
      pid = ProcessHandle.current().pid().toInt(),
      toolingApiVersion = "build-grpc",
      supportsModelSnapshot = true,
      supportsQueryService = true,
    ),
  )

  fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult> = future {
    val response = binaryApi.buildInitialize(
      BuildInitializeRequest(
        workspaceRoot = params.directory,
        buildSystemId = "gradle",
        clientName = "tooling-api-client",
        clientVersion = "binary-facade",
      ),
    )
    val realSyncCapability = "gradle.tooling.real-sync"
    val isRealSync = realSyncCapability in response.negotiatedCapabilities
    InitializeResult(
      isSuccessful = isRealSync,
      failure = if (isRealSync) null else TaskExecutionResult.Failure.UNSUPPORTED_CONFIGURATION,
      requestId = params.requestId,
      negotiatedOperationTypes = response.negotiatedCapabilities,
      supportsModelSnapshot = isRealSync,
      supportsQueryService = isRealSync,
      supportsPhasedAction = isRealSync,
    )
  }

  fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult> = future {
    binaryApi.buildTargetCompile(
      BuildTargetCompileRequest(
        buildId = "task-${System.currentTimeMillis()}",
        targetIds = message.tasks.ifEmpty { listOf(":") },
        arguments = message.arguments + message.jvmArguments,
      ),
    ).first { it.kind == BuildEventKind.BUILD_EVENT_KIND_FINISHED }
    TaskExecutionResult.SUCCESS
  }

  fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult> = future {
    binaryApi.buildTargetCompile(
      BuildTargetCompileRequest(
        buildId = request.requestId.ifBlank { "exec-${System.currentTimeMillis()}" },
        targetIds = request.tasks.ifEmpty { listOf(":") },
        arguments = request.arguments + request.jvmArguments,
      ),
    ).first { it.kind == BuildEventKind.BUILD_EVENT_KIND_FINISHED }
    ExecutionResult.SUCCESS.copy(requestId = request.requestId)
  }

  fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> = future {
    val result = binaryApi.cancelBuild(BuildCancelRequest(buildId = "current"))
    BuildCancellationRequestResult(result.accepted)
  }

  fun shutdown(): CompletableFuture<Void> =
    future<Unit> {
      binaryApi.shutdown(BuildShutdownRequest("tooling-api-facade shutdown"))
    }.thenApply { null }

  private fun <T> future(block: suspend () -> T): CompletableFuture<T> =
    CompletableFuture.supplyAsync({ kotlinx.coroutines.runBlocking { block() } }, executor)
}
