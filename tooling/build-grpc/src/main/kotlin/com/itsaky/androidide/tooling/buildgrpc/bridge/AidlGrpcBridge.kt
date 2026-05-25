package com.itsaky.androidide.tooling.buildgrpc.bridge

import com.itsaky.androidide.tooling.buildgrpc.api.BuildProtocolContract

/**
 * Keeps protocol concerns separated:
 * 1) Kotlin/gRPC service implementation.
 * 2) AIDL JSON bridge for low-overhead IPC handoff.
 */
class AidlGrpcBridge(
  private val contract: BuildProtocolContract,
) {
  fun describe(): String {
    return buildString {
      append(contract.serverName)
      append('@')
      append(contract.protocolVersion)
      append(':')
      append(contract.supportedFeatures().sorted().joinToString(","))
    }
  }
}
