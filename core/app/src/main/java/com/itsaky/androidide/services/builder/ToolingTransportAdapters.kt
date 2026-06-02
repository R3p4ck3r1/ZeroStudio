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

/** App-dex-safe adapter that exposes the launched JSON-RPC server through the transport SPI. */
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
    val requestedMode: ToolingTransportMode?,
    val resolvedMode: ToolingTransportMode,
    val reapiWorkspacePath: String,
    val reapiWorkspaceReady: Boolean,
    val reason: String? = null,
) {
  companion object {
    fun legacy(
        requestedValue: String,
        requestedMode: ToolingTransportMode?,
        reapiWorkspacePath: String,
        reapiWorkspaceReady: Boolean,
        reason: String? = null,
    ): TransportSelectionResult {
      return TransportSelectionResult(
          requestedValue = requestedValue,
          requestedMode = requestedMode,
          resolvedMode = ToolingTransportMode.LEGACY_JSONRPC,
          reapiWorkspacePath = reapiWorkspacePath,
          reapiWorkspaceReady = reapiWorkspaceReady,
          reason = reason,
      )
    }

    fun integrated(
        requestedValue: String,
        reapiWorkspacePath: String,
        reapiWorkspaceReady: Boolean,
        reason: String? = null,
    ): TransportSelectionResult {
      return TransportSelectionResult(
          requestedValue = requestedValue,
          requestedMode = ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI,
          resolvedMode = ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI,
          reapiWorkspacePath = reapiWorkspacePath,
          reapiWorkspaceReady = reapiWorkspaceReady,
          reason = reason,
      )
    }
  }
}

/**
 * App-side transport selector.
 *
 * The full tooling implementation is delivered as the external tooling jar, not as classes on the
 * app dex path. This selector must therefore only create endpoints whose classes live in the app APK.
 */
internal object ToolingServerEndpointFactories {
  private val log = LoggerFactory.getLogger(ToolingServerEndpointFactories::class.java)

  const val TRANSPORT_SWITCH_PROPERTY: String = "androidide.tooling.transport"
  const val LEGACY: String = "legacy"
  const val REAPI_WORKSPACE_PATH: String = "tooling/reapi"

  fun resolveSelection(value: String?): TransportSelectionResult {
    val configured = value.orEmpty().ifBlank { LEGACY }.trim().lowercase()
    val requestedMode = ToolingTransportMode.fromWireValue(configured)
    val workspaceReady = isReapiWorkspaceReady()
    return when (requestedMode) {
      ToolingTransportMode.LEGACY_JSONRPC ->
          TransportSelectionResult.legacy(
              requestedValue = configured,
              requestedMode = requestedMode,
              reapiWorkspacePath = REAPI_WORKSPACE_PATH,
              reapiWorkspaceReady = workspaceReady,
          )
      ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI ->
          TransportSelectionResult.integrated(
              requestedValue = configured,
              reapiWorkspacePath = REAPI_WORKSPACE_PATH,
              reapiWorkspaceReady = workspaceReady,
              reason =
                  "Using app-process build-grpc binary endpoint; REAPI workspace ready=$workspaceReady.",
          )
      null ->
          TransportSelectionResult.legacy(
              requestedValue = configured,
              requestedMode = null,
              reapiWorkspacePath = REAPI_WORKSPACE_PATH,
              reapiWorkspaceReady = workspaceReady,
              reason = "Unknown transport value '$configured'",
          )
    }
  }

  fun fromSelection(selection: TransportSelectionResult): ToolingServerEndpointFactory {
    if (selection.reason != null) {
      val isFallback =
          selection.requestedMode == null || selection.requestedMode != selection.resolvedMode
      val message = "Tooling transport configured='{}' resolved to '{}' because {}"
      if (isFallback) {
        log.warn(
            message,
            selection.requestedValue,
            selection.resolvedMode.wireValue,
            selection.reason,
        )
      } else {
        log.info(
            message,
            selection.requestedValue,
            selection.resolvedMode.wireValue,
            selection.reason,
        )
      }
    }
    return when (selection.resolvedMode) {
      ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI ->
          ToolingServerEndpointFactory { _ -> IntegratedBinaryToolingServerEndpoint() }
      ToolingTransportMode.LEGACY_JSONRPC -> ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
    }
  }

  private fun isReapiWorkspaceReady(): Boolean {
    val root = File(REAPI_WORKSPACE_PATH)
    return root.exists() && File(root, ".git").exists()
  }
}

