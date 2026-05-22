package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode
import com.itsaky.androidide.tooling.api.transport.ToolingTransportServerEndpoint
import org.slf4j.LoggerFactory

/**
 * Factory contract for creating a concrete tooling transport endpoint.
 *
 * Transport stack selection is controlled by `androidide.tooling.transport`:
 * - `legacy` (default): JSON-RPC/LSP4J based server proxy.
 * - `integrated`: AIDL + gRPC(UDS) + REAPI integrated stack (reserved, not implemented yet).
 */
fun interface ToolingServerEndpointFactory {
  fun create(server: IToolingApiServer): ToolingTransportServerEndpoint
}

object ToolingServerEndpointFactories {
  private val log = LoggerFactory.getLogger(ToolingServerEndpointFactories::class.java)

  const val TRANSPORT_SWITCH_PROPERTY: String = "androidide.tooling.transport"
  const val LEGACY: String = "legacy"

  @JvmStatic
  fun fromSystemProperty(): ToolingServerEndpointFactory {
    val configured = System.getProperty(TRANSPORT_SWITCH_PROPERTY, LEGACY)
    return fromSelection(resolveSelection(configured))
  }

  @JvmStatic
  fun fromTransportValue(value: String?): ToolingServerEndpointFactory {
    return fromSelection(resolveSelection(value))
  }

  @JvmStatic
  fun resolveSelection(value: String?): TransportSelectionResult {
    val configured = value.orEmpty().ifBlank { LEGACY }.trim().lowercase()
    val mode = ToolingTransportMode.fromWireValue(configured)
    return when (mode) {
      ToolingTransportMode.LEGACY_JSONRPC ->
          TransportSelectionResult(
              requestedValue = configured,
              parsedMode = mode,
              effectiveMode = ToolingTransportMode.LEGACY_JSONRPC,
          )
      ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI ->
          TransportSelectionResult(
              requestedValue = configured,
              parsedMode = mode,
              effectiveMode = ToolingTransportMode.LEGACY_JSONRPC,
              fallbackReason = "Integrated transport stack '$configured' is in transitional gateway mode",
          )
      null ->
          TransportSelectionResult(
              requestedValue = configured,
              parsedMode = null,
              effectiveMode = ToolingTransportMode.LEGACY_JSONRPC,
              fallbackReason = "Unknown transport value '$configured'",
          )
    }
  }

  @JvmStatic
  fun fromSelection(selection: TransportSelectionResult): ToolingServerEndpointFactory {
    when (selection.parsedMode) {
      ToolingTransportMode.LEGACY_JSONRPC -> Unit
      ToolingTransportMode.INTEGRATED_AIDL_GRPC_REAPI -> {
        log.info(
            "Integrated transport stack '{}' is routed through transitional gateway endpoint.",
            selection.requestedValue,
        )
        return ToolingServerEndpointFactory(::IntegratedToolingServerEndpointGateway)
      }
      null -> {
        log.warn(
            "Unknown transport switch '{}' from -D{}. Falling back to '{}'.",
            selection.requestedValue,
            TRANSPORT_SWITCH_PROPERTY,
            LEGACY,
        )
      }
    }
    return ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
  }
}
