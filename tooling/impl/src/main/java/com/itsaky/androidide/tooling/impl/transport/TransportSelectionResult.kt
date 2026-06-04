package com.itsaky.androidide.tooling.impl.transport

import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode

/** Immutable unified transport configuration produced by [ToolingServerEndpointFactories]. */
data class TransportSelectionResult(
    val requestedValue: String,
    val mode: ToolingTransportMode,
    val reapiWorkspacePath: String,
    val reapiWorkspaceReady: Boolean,
    val reason: String? = null,
)
