/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.tooling.api.models

import org.gradle.tooling.events.OperationType

/**
 * Metadata about the tooling server.
 *
 * @param pid The process id of the tooling server.
 * @author Akash Yadav
 */
data class ToolingServerMetadata(
    val pid: Int,
    val toolingApiVersion: String = "9.5.1",
    val supportsPhasedBuildAction: Boolean = true,
    val supportedOperationTypes: Set<OperationType> = emptySet(),
    val negotiatedOperationTypes: Set<OperationType> = emptySet(),
    val maxProgressEventsPerSecond: Int? = null,
)
