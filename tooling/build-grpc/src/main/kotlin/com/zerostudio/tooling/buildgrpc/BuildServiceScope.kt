package com.zerostudio.tooling.buildgrpc

/**
 * Protocol consumer scopes tailored for current codebase modules.
 *
 * - tooling/* : protocol providers, adapters, model layers
 * - core/project : project lifecycle and build orchestration
 * - core/app : app-level UI/commands invoking build operations
 */
enum class BuildServiceScope {
  TOOLING,
  CORE_PROJECT,
  CORE_APP,
  EXTERNAL,
}

data class BuildSessionContext(
  val scope: BuildServiceScope,
  val workspaceRoot: String,
  val backendHint: String? = null,
  val featureFlags: Set<String> = emptySet(),
)
