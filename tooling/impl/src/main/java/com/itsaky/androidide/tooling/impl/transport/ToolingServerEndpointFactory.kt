package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.IToolingApiServer
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
  const val AIDL: String = "aidl"
  const val GRPC_UDS: String = "grpc-uds"

  @JvmStatic
  fun fromSystemProperty(): ToolingServerEndpointFactory {
    val configured = System.getProperty(TRANSPORT_SWITCH_PROPERTY, LEGACY)
    return fromTransportValue(configured)
  }

  @JvmStatic
  fun fromTransportValue(value: String?): ToolingServerEndpointFactory {
    val configured = value.orEmpty().ifBlank { LEGACY }.trim().lowercase()
    return when (configured) {
      LEGACY -> ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
      AIDL,
      GRPC_UDS -> {
        log.info(
            "Transport '{}' is not implemented yet. Falling back to '{}' endpoint.",
            configured,
            LEGACY,
        )
        ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
      }
      else -> {
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
