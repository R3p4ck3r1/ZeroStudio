package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import kotlinx.coroutines.flow.Flow

/**
 * Binary build-service API intended to replace legacy lsp4j-rpc + gson transport path.
 *
 * Design goal mirrors BSP-style M+N interoperability:
 * - IDE/client binds this API once.
 * - Build-system backend (Gradle/Bazel/...) is selected by session/build context.
 */
interface BinaryBuildServiceApi {
  suspend fun initialize(request: BinaryInitializeRequest): BinaryInitializeResponse

  suspend fun workspaceBuildTargets(request: WorkspaceBuildTargetsRequest): WorkspaceBuildTargetsResponse

  fun compile(request: BuildTargetCompileRequest): Flow<BuildEventEnvelope>

  fun test(request: BuildTargetTestRequest): Flow<BuildEventEnvelope>

  fun run(request: BuildTargetRunRequest): Flow<BuildEventEnvelope>

  suspend fun dependencyModules(request: BuildTargetDependencyModulesRequest): BuildTargetDependencyModulesResponse

  suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult

  suspend fun cancelBuild(buildId: String): Boolean

  suspend fun shutdown(reason: String): Boolean
}

data class BinaryInitializeRequest(
  val workspaceRoot: String,
  val buildSystem: BuildSystem,
  val clientName: String,
  val clientVersion: String,
  val callerId: String,
  val capabilities: Set<String> = emptySet(),
)

data class BinaryInitializeResponse(
  val server: BuildServerInfo,
  val protocolVersion: String,
  val negotiatedFeatures: Set<String>,
)

data class WorkspaceBuildTargetsRequest(
  val workspaceRoot: String,
)

data class WorkspaceBuildTargetsResponse(
  val targets: List<BuildTargetInfo>,
)

data class BuildTargetInfo(
  val id: String,
  val displayName: String,
  val baseDirectory: String,
  val languageIds: List<String>,
  val tags: Set<String> = emptySet(),
)

data class BuildTargetCompileRequest(
  val buildId: String,
  val targetIds: List<String>,
  val arguments: List<String> = emptyList(),
)

data class BuildTargetTestRequest(
  val buildId: String,
  val targetIds: List<String>,
  val arguments: List<String> = emptyList(),
)

data class BuildTargetRunRequest(
  val buildId: String,
  val targetId: String,
  val arguments: List<String> = emptyList(),
)

data class BuildTargetDependencyModulesRequest(
  val targetIds: List<String>,
)

data class BuildTargetDependencyModulesResponse(
  val dependencyModulesByTargetId: Map<String, List<String>>,
)
