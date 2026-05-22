/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package com.itsaky.androidide.tooling.api.messages

import java.io.Serializable
import org.gradle.tooling.events.OperationType

/**
 * Optional capabilities/preferences sent by tooling client during initialization.
 */
data class ToolingClientCapabilities(
    val requestedOperationTypes: Set<OperationType> = emptySet(),
    val maxEventsPerSecond: Int? = null,
    val preferLightweightSync: Boolean = false,
    val requestModelSnapshotSupport: Boolean = false,
    val requestQueryServiceSupport: Boolean = false,
    val requestPhasedActionSupport: Boolean = false,
) : Serializable
