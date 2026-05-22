package com.itsaky.androidide.tooling.api.transport

/** Transport switch values for tooling server endpoint selection. */
enum class ToolingTransportMode(val wireValue: String) {
  LEGACY("legacy"),
  AIDL("aidl"),
  GRPC_UDS("grpc-uds");

  companion object {
    @JvmStatic
    fun fromWireValue(value: String?): ToolingTransportMode? {
      val normalized = value.orEmpty().trim().lowercase()
      if (normalized.isBlank()) {
        return LEGACY
      }
      return entries.firstOrNull { it.wireValue == normalized }
    }
  }
}
