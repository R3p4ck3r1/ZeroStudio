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

package com.itsaky.androidide.tooling.api.messages

import java.util.UUID

/**
 * Protocol-neutral request for executing Gradle work.
 *
 * This extends the legacy task-only request with optional launcher arguments, JVM arguments,
 * environment variables and requested operation progress types so callers do not have to depend on
 * Gradle Tooling API classes directly.
 */
data class ExecutionRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val tasks: List<String> = emptyList(),
    val arguments: List<String> = emptyList(),
    val jvmArguments: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val operationTypes: Set<OperationType> = emptySet(),
) {
  enum class OperationType {
    TASK,
    TEST,
    PROJECT_CONFIGURATION,
    WORK_ITEM,
    BUILD_PHASE,
    BUILD,
    PROBLEM,
  }
}
