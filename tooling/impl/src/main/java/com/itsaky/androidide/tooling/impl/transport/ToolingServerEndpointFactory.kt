package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode
import com.itsaky.androidide.tooling.api.transport.ToolingTransportServerEndpoint
import java.io.File
import org.slf4j.LoggerFactory

/** Factory contract for creating the unified build-grpc tooling endpoint. */
fun interface ToolingServerEndpointFactory {
  fun create(server: IToolingApiServer): ToolingTransportServerEndpoint
}

object ToolingServerEndpointFactories {
  private val log = LoggerFactory.getLogger(ToolingServerEndpointFactories::class.java)

  const val TRANSPORT_SWITCH_PROPERTY: String = "androidide.tooling.transport"
  const val UNIFIED: String = "build-grpc"
  const val REAPI_WORKSPACE_PATH: String = "tooling/reapi"

  @JvmStatic
  fun fromSystemProperty(): ToolingServerEndpointFactory =
      fromSelection(resolveSelection(System.getProperty(TRANSPORT_SWITCH_PROPERTY, UNIFIED)))

  @JvmStatic
  fun fromTransportValue(value: String?): ToolingServerEndpointFactory =
      fromSelection(resolveSelection(value))

  @JvmStatic
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
              "Using unified build-grpc binary protocol; REAPI workspace ready=$workspaceReady."
            } else {
              "Transport value '$requested' is an alias; unified build-grpc binary protocol is mandatory. REAPI workspace ready=$workspaceReady."
            },
    )
  }

  @JvmStatic
  fun fromSelection(selection: TransportSelectionResult): ToolingServerEndpointFactory {
    log.info(
        "Tooling transport '{}' resolved to mandatory unified '{}': {}",
        selection.requestedValue,
        selection.mode.wireValue,
        selection.reason,
    )
    return ToolingServerEndpointFactory(::IntegratedToolingServerEndpointGateway)
  }

  private fun isReapiWorkspaceReady(): Boolean {
    val root = File(REAPI_WORKSPACE_PATH)
    return root.exists() && File(root, ".git").exists()
  }
}
