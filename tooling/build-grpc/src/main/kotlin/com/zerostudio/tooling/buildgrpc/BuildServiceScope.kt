package com.zerostudio.tooling.buildgrpc

/**
 * Build service consumer scope.
 *
 * Scope is intentionally capability-based (not hard-coded to repository module paths),
 * so protocol can serve current project internals and future external integrations.
 */
enum class BuildServiceScope {
  /** Internal IDE/runtime callers. */
  INTERNAL,

  /** External or third-party caller. */
  EXTERNAL,
}

data class BuildSessionContext(
  val scope: BuildServiceScope,
  /** Logical caller id, e.g. package/class/component identifier. */
  val callerId: String,
  val workspaceRoot: String,
  val backendHint: String? = null,
  val featureFlags: Set<String> = emptySet(),
)

object BuildSessionContextResolver {
  private const val CAP_INTERNAL = "scope:internal"
  private const val CAP_EXTERNAL = "scope:external"
  private const val CAP_CALLER_PREFIX = "caller:"
  private const val CAP_BACKEND_PREFIX = "backend:"

  fun fromInit(request: BuildInit): BuildSessionContext {
    val caps = request.capabilities.toSet()
    val scope = when {
      CAP_EXTERNAL in caps -> BuildServiceScope.EXTERNAL
      else -> BuildServiceScope.INTERNAL
    }
    val callerId = caps.firstOrNull { it.startsWith(CAP_CALLER_PREFIX) }
      ?.removePrefix(CAP_CALLER_PREFIX)
      ?.ifBlank { "unknown" }
      ?: "unknown"
    val backendHint = caps.firstOrNull { it.startsWith(CAP_BACKEND_PREFIX) }
      ?.removePrefix(CAP_BACKEND_PREFIX)
      ?.ifBlank { null }

    return BuildSessionContext(
      scope = scope,
      callerId = callerId,
      workspaceRoot = request.workspaceRoot,
      backendHint = backendHint,
      featureFlags = caps - CAP_INTERNAL - CAP_EXTERNAL,
    )
  }
}
