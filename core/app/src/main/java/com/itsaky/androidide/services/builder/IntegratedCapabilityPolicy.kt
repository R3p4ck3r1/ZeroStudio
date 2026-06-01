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
package com.itsaky.androidide.services.builder

import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult

/**
 * App-side session capability policy for the integrated transport stack.
 *
 * This class lives in the APK module because [GradleBuildService] instantiates it when Android
 * creates the foreground service. Keeping it out of the compile-only tooling implementation module
 * prevents startup crashes when the external tooling implementation jar is not on the app dex path.
 */
internal class IntegratedCapabilityPolicy {

  data class CapabilitySnapshot(
      val supportsPhasedAction: Boolean,
      val supportsModelSnapshot: Boolean,
      val supportsQueryService: Boolean,
      val negotiatedOperationTypes: Set<String>,
  ) {
    companion object {
      val DEFAULT = CapabilitySnapshot(false, false, false, emptySet())

      fun fromInitialize(result: InitializeResult): CapabilitySnapshot {
        return CapabilitySnapshot(
            supportsPhasedAction = result.supportsPhasedAction,
            supportsModelSnapshot = result.supportsModelSnapshot,
            supportsQueryService = result.supportsQueryService,
            negotiatedOperationTypes = result.negotiatedOperationTypes,
        )
      }
    }
  }

  @Volatile private var snapshot: CapabilitySnapshot = CapabilitySnapshot.DEFAULT

  fun reset() {
    snapshot = CapabilitySnapshot.DEFAULT
  }

  fun updateFromInitialize(result: InitializeResult) {
    snapshot = CapabilitySnapshot.fromInitialize(result)
  }

  fun current(): CapabilitySnapshot = snapshot

  fun applyToExecutionRequest(
      request: ExecutionRequest,
      defaultOps: Set<String>,
  ): ExecutionRequest {
    val cap = snapshot
    val effectiveOps =
        when {
          request.operationTypes.isNotEmpty() -> request.operationTypes
          cap.negotiatedOperationTypes.isNotEmpty() -> cap.negotiatedOperationTypes
          else -> defaultOps
        }

    return request.copy(
        operationTypes = effectiveOps,
        tasks = request.tasks.filter { it.isNotBlank() },
        arguments = request.arguments.filter { it.isNotBlank() },
        jvmArguments = request.jvmArguments.filter { it.isNotBlank() },
    )
  }
}
