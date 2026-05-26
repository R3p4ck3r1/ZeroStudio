package com.itsaky.androidide.tooling.api.util

/**
 * Legacy JSON-RPC launcher removed.
 *
 * Use [BinaryToolingLauncher] with build-grpc AIDL + gRPC + REAPI binary protocol.
 */
@Deprecated(
  message = "JSON-RPC launcher is removed. Use BinaryToolingLauncher.",
  replaceWith = ReplaceWith("BinaryToolingLauncher"),
)
object ToolingApiLauncher {
  @JvmStatic
  fun removed(): Nothing = error(
    "ToolingApiLauncher(JSON-RPC/Gson/LSP4J) has been removed. " +
      "Migrate to BinaryToolingLauncher and build-grpc binary protocol.",
  )
}
