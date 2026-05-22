package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode

data class TransportSelectionResult(
    val requestedValue: String,
    val parsedMode: ToolingTransportMode?,
    val effectiveMode: ToolingTransportMode,
    val fallbackReason: String? = null,
    val reapiWorkspacePath: String = "tooling/reapi",
    val reapiWorkspaceReady: Boolean = false,
)
