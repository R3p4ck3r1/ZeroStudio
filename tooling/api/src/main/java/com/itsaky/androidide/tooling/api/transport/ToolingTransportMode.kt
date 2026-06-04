package com.itsaky.androidide.tooling.api.transport

/**
 * Single build transport used by the tooling stack.
 *
 * Older string values such as `legacy` and `integrated` are accepted only as compatibility aliases;
 * they no longer select different implementations. All client/server calls are routed through the
 * unified build-grpc binary protocol surface.
 */
enum class ToolingTransportMode(val wireValue: String) {
  UNIFIED_BUILD_GRPC("build-grpc");

  companion object {
    @JvmStatic
    fun fromWireValue(value: String?): ToolingTransportMode {
      return UNIFIED_BUILD_GRPC
    }
  }
}
