/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.services.builder

import com.itsaky.androidide.tooling.api.IToolingApiClient
import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.LogMessageParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.BuildInfo
import com.itsaky.androidide.tooling.api.messages.result.BuildResult
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.GradleWrapperCheckResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import com.itsaky.androidide.tooling.api.transport.ToolingTransportClientObserver
import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode
import com.itsaky.androidide.tooling.api.transport.ToolingTransportServerEndpoint
import com.itsaky.androidide.tooling.events.ProgressEvent
import java.io.File
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory

/** Legacy JSON-RPC callback adapter used by the Android app process. */
internal class LegacyToolingClientAdapter(private val observer: ToolingTransportClientObserver) :
    IToolingApiClient {

  override fun logMessage(params: LogMessageParams) {
    observer.onLogMessage(params.tag, params.level, params.message)
  }

  override fun logOutput(line: String) {
    observer.onLogMessage("ToolingOutput", 'I', line)
  }

  override fun prepareBuild(buildInfo: BuildInfo) {
    observer.onBuildPrepared(buildInfo)
  }

  override fun onBuildSuccessful(result: BuildResult) {
    observer.onBuildSuccessful(result)
  }

  override fun onBuildFailed(result: BuildResult) {
    observer.onBuildFailed(result)
  }

  override fun onProgressEvent(event: ProgressEvent) {
    observer.onProgressEvent(event)
  }

  override fun getBuildArguments(): CompletableFuture<List<String>> = observer.buildArguments()

  override fun checkGradleWrapperAvailability(): CompletableFuture<GradleWrapperCheckResult> =
      CompletableFuture.completedFuture(GradleWrapperCheckResult(true))
}

/** App-dex-safe adapter retained only for tests that wrap a launched Tooling API server. */
private class LegacyToolingServerEndpoint(private val delegate: IToolingApiServer) :
    ToolingTransportServerEndpoint {

  override fun metadata(): CompletableFuture<ToolingServerMetadata> = delegate.metadata()

  override fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult> =
      delegate.initialize(params)

  override fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult> =
      delegate.executeTasks(message)

  override fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult> =
      delegate.execute(request)

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> =
      delegate.cancelCurrentBuild()

  override fun shutdown(): CompletableFuture<Void> = delegate.shutdown()
}

internal fun interface ToolingServerEndpointFactory {
  fun create(server: IToolingApiServer): ToolingTransportServerEndpoint
}

internal data class TransportSelectionResult(
    val requestedValue: String,
    val mode: ToolingTransportMode,
    val reapiWorkspacePath: String,
    val reapiWorkspaceReady: Boolean,
    val reason: String,
)

/**
 * App-side unified transport selector.
 *
 * There is no longer a legacy/integrated mode split. Any configured value is treated as an alias
 * and all requests use the app-process build-grpc binary endpoint.
 */
internal object ToolingServerEndpointFactories {
  private val log = LoggerFactory.getLogger(ToolingServerEndpointFactories::class.java)

  const val TRANSPORT_SWITCH_PROPERTY: String = "androidide.tooling.transport"
  const val UNIFIED: String = "build-grpc"
  const val REAPI_WORKSPACE_PATH: String = "tooling/reapi"

  fun resolveSelection(value: String?): TransportSelectionResult {
    val requested = value.orEmpty().ifBlank { UNIFIED }.trim().lowercase()
    val workspaceReady = isReapiWorkspaceReady()
    return TransportSelectionResult(
        requestedValue = requested,
        mode = ToolingTransportMode.UNIFIED_BUILD_GRPC,
        reapiWorkspacePath = REAPI_WORKSPACE_PATH,
        reapiWorkspaceReady = workspaceReady,
        reason =
            if (requested == UNIFIED) {
              "Using app-process unified build-grpc binary endpoint; REAPI workspace ready=$workspaceReady."
            } else {
              "Transport value '$requested' is an alias; app-process unified build-grpc binary endpoint is mandatory. REAPI workspace ready=$workspaceReady."
            },
    )
  }

  fun fromSelection(selection: TransportSelectionResult): ToolingServerEndpointFactory {
    log.info(
        "Tooling transport '{}' resolved to mandatory unified '{}': {}",
        selection.requestedValue,
        selection.mode.wireValue,
        selection.reason,
    )
    return ToolingServerEndpointFactory { _ -> IntegratedBinaryToolingServerEndpoint() }
  }

  private fun isReapiWorkspaceReady(): Boolean {
    val root = File(REAPI_WORKSPACE_PATH)
    return root.exists() && File(root, ".git").exists()
  }
}
