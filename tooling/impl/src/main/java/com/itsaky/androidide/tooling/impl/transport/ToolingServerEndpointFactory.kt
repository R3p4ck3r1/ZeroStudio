package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode
import com.itsaky.androidide.tooling.api.transport.ToolingTransportServerEndpoint
import org.slf4j.LoggerFactory

/**
 * Factory contract for creating a concrete tooling transport endpoint.
 *
 * Transport selection is controlled by `androidide.tooling.transport`:
 * - `legacy` (default): JSON-RPC/LSP4J based server proxy.
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
    return fromTransportValue(configured)
  }

  @JvmStatic
  fun fromTransportValue(value: String?): ToolingServerEndpointFactory {
    val configured = value.orEmpty().ifBlank { LEGACY }.trim().lowercase()
    val mode = ToolingTransportMode.fromWireValue(configured)
    return when (mode) {
      ToolingTransportMode.LEGACY -> ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
      ToolingTransportMode.AIDL,
      ToolingTransportMode.GRPC_UDS -> {
        log.info(
            "Transport '{}' is not implemented yet. Falling back to '{}' endpoint.",
            configured,
            LEGACY,
        )
        ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
      }
      null -> {
        log.warn(
            "Unknown transport switch '{}' from -D{}. Falling back to '{}'.",
            configured,
            TRANSPORT_SWITCH_PROPERTY,
            LEGACY,
        )
        ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
      }
    }
  }
}
