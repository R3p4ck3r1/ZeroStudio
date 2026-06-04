package com.itsaky.androidide.services.builder

import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.transport.ToolingTransportMode

/**
 * Centralized client-side routing policy for deciding whether build requests should be executed
 * through Tooling transport execute(request) or through local gradlew shell invocation.
 */
internal class IntegratedExecutionRoutingPolicy {

  data class RoutingContext(
      val executeEnabled: Boolean,
      val transportMode: ToolingTransportMode,
      val initializeResult: InitializeResult?,
      val capabilitySnapshot: IntegratedCapabilityPolicy.CapabilitySnapshot,
      val tasks: List<String>,
  )

  data class RoutingDecision(
      val useToolingExecute: Boolean,
      val reason: String,
      val unifiedMode: Boolean,
      val capabilityReady: Boolean,
  )

  fun decide(context: RoutingContext): RoutingDecision {
    if (!context.executeEnabled) {
      return RoutingDecision(
          useToolingExecute = false,
          reason = "tooling_execute_disabled",
          unifiedMode = false,
          capabilityReady = false,
      )
    }

    val unifiedMode = context.transportMode == ToolingTransportMode.UNIFIED_BUILD_GRPC
    val init = context.initializeResult
    val hasNegotiatedOps =
        (init?.negotiatedOperationTypes?.isNotEmpty() == true) ||
            context.capabilitySnapshot.negotiatedOperationTypes.isNotEmpty()

    // Unified build-grpc transport requires initialize negotiation to be available first, preventing blind
    // request dispatch before capability convergence.
    if (unifiedMode && init == null) {
      return RoutingDecision(
          useToolingExecute = false,
          reason = "unified_waiting_initialize_negotiation",
          unifiedMode = true,
          capabilityReady = false,
      )
    }

    if (unifiedMode && !hasNegotiatedOps) {
      return RoutingDecision(
          useToolingExecute = false,
          reason = "unified_missing_negotiated_operation_types",
          unifiedMode = true,
          capabilityReady = false,
      )
    }

    // If cleaning is requested, prefer local shell path as current tooling execute contract does
    // not fully model global daemon/process cleanup semantics yet.
    if (containsCleanupTask(context.tasks)) {
      return RoutingDecision(
          useToolingExecute = false,
          reason = "cleanup_task_prefers_shell_path",
          unifiedMode = unifiedMode,
          capabilityReady = hasNegotiatedOps,
      )
    }

    return RoutingDecision(
        useToolingExecute = true,
        reason = "unified_build_grpc_capability_ready",
        unifiedMode = unifiedMode,
        capabilityReady = hasNegotiatedOps,
    )
  }

  private fun containsCleanupTask(tasks: List<String>): Boolean {
    return tasks.any {
      val t = it.lowercase()
      t.contains("clean") || t.contains("stop")
    }
  }
}
