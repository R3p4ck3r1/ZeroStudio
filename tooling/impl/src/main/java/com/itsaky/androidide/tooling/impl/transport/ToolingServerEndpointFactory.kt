package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.IToolingApiServer
import com.itsaky.androidide.tooling.api.transport.ToolingTransportServerEndpoint

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
  const val TRANSPORT_SWITCH_PROPERTY: String = "androidide.tooling.transport"
  const val LEGACY: String = "legacy"

  @JvmStatic
  fun fromSystemProperty(): ToolingServerEndpointFactory {
    return when (System.getProperty(TRANSPORT_SWITCH_PROPERTY, LEGACY).trim().lowercase()) {
      LEGACY -> ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
      else -> ToolingServerEndpointFactory(::LegacyToolingServerEndpoint)
    }
  }
}
