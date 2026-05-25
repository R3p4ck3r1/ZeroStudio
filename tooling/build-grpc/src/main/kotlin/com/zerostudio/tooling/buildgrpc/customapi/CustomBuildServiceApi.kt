package com.zerostudio.tooling.buildgrpc.customapi

import com.zerostudio.tooling.buildgrpc.ActionExecutionRequest
import com.zerostudio.tooling.buildgrpc.ActionExecutionResult
import com.zerostudio.tooling.buildgrpc.BinaryBuildServiceApi
import com.zerostudio.tooling.buildgrpc.BinaryInitializeRequest
import com.zerostudio.tooling.buildgrpc.BinaryInitializeResponse
import com.zerostudio.tooling.buildgrpc.BuildTargetCompileRequest
import com.zerostudio.tooling.buildgrpc.BuildTargetDependencyModulesRequest
import com.zerostudio.tooling.buildgrpc.BuildTargetDependencyModulesResponse
import com.zerostudio.tooling.buildgrpc.BuildTargetRunRequest
import com.zerostudio.tooling.buildgrpc.BuildTargetTestRequest
import com.zerostudio.tooling.buildgrpc.WorkspaceBuildTargetsRequest
import com.zerostudio.tooling.buildgrpc.WorkspaceBuildTargetsResponse
import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import kotlinx.coroutines.flow.Flow

/**
 * Dedicated custom API surface for replacing legacy tooling/api JSON-RPC contracts
 * with binary gRPC-oriented build service calls.
 */
interface CustomBuildServiceApi {
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

class DefaultCustomBuildServiceApi(
  private val delegate: BinaryBuildServiceApi,
) : CustomBuildServiceApi {
  override suspend fun initialize(request: BinaryInitializeRequest): BinaryInitializeResponse =
    delegate.initialize(request)

  override suspend fun workspaceBuildTargets(request: WorkspaceBuildTargetsRequest): WorkspaceBuildTargetsResponse =
    delegate.workspaceBuildTargets(request)

  override fun compile(request: BuildTargetCompileRequest): Flow<BuildEventEnvelope> = delegate.compile(request)

  override fun test(request: BuildTargetTestRequest): Flow<BuildEventEnvelope> = delegate.test(request)

  override fun run(request: BuildTargetRunRequest): Flow<BuildEventEnvelope> = delegate.run(request)

  override suspend fun dependencyModules(request: BuildTargetDependencyModulesRequest): BuildTargetDependencyModulesResponse =
    delegate.dependencyModules(request)

  override suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult =
    delegate.executeAction(request)

  override suspend fun cancelBuild(buildId: String): Boolean = delegate.cancelBuild(buildId)

  override suspend fun shutdown(reason: String): Boolean = delegate.shutdown(reason)
}
