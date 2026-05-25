package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import com.zerostudio.tooling.buildgrpc.proto.BuildMode
import kotlinx.coroutines.flow.Flow

/**
 * Default protocol implementation focused on standardized binary workflow.
 */
class DefaultBinaryBuildServiceApi(
  private val module: RoutingBuildGrpcModule,
) : BinaryBuildServiceApi {

  override suspend fun initialize(request: BinaryInitializeRequest): BinaryInitializeResponse {
    val init = BuildInit(
      workspaceRoot = request.workspaceRoot,
      clientName = request.clientName,
      clientVersion = request.clientVersion,
      capabilities = request.capabilities + setOf(
        "buildSystem:${request.buildSystem.name.lowercase()}",
        "caller:${request.callerId}",
      ),
    )
    val info = module.initialize(init)
    return BinaryInitializeResponse(
      server = info,
      protocolVersion = "1.0.0",
      negotiatedFeatures = info.protocolFeatures.toSet(),
    )
  }

  override suspend fun workspaceBuildTargets(request: WorkspaceBuildTargetsRequest): WorkspaceBuildTargetsResponse {
    return WorkspaceBuildTargetsResponse(emptyList())
  }

  override fun compile(request: BuildTargetCompileRequest): Flow<BuildEventEnvelope> =
    module.startBuild(
      BuildStart(
        buildId = request.buildId,
        targets = request.targetIds,
        options = mapOf(
          "build.mode" to BuildMode.BUILD_MODE_INCREMENTAL.name,
          "build.args" to request.arguments.joinToString(" "),
        ),
      ),
    )

  override fun test(request: BuildTargetTestRequest): Flow<BuildEventEnvelope> =
    module.startBuild(
      BuildStart(
        buildId = request.buildId,
        targets = request.targetIds,
        options = mapOf(
          "build.mode" to BuildMode.BUILD_MODE_TEST.name,
          "build.args" to request.arguments.joinToString(" "),
        ),
      ),
    )

  override fun run(request: BuildTargetRunRequest): Flow<BuildEventEnvelope> =
    module.startBuild(
      BuildStart(
        buildId = request.buildId,
        targets = listOf(request.targetId),
        options = mapOf(
          "build.mode" to BuildMode.BUILD_MODE_INCREMENTAL.name,
          "build.args" to request.arguments.joinToString(" "),
          "build.intent" to "run",
        ),
      ),
    )

  override suspend fun dependencyModules(request: BuildTargetDependencyModulesRequest): BuildTargetDependencyModulesResponse {
    return BuildTargetDependencyModulesResponse(
      dependencyModulesByTargetId = request.targetIds.associateWith { emptyList<String>() },
    )
  }

  override suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult =
    module.executeAction(request)

  override suspend fun cancelBuild(buildId: String): Boolean {
    // Next phase: explicit cancellation token integration per backend.
    return buildId.isNotBlank()
  }

  override suspend fun shutdown(reason: String): Boolean = module.shutdown(reason)
}
