package com.zerostudio.tooling.buildgrpc

import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams

/**
 * Migration mapper from legacy tooling-api request models to binary protocol requests.
 */
object LegacyToolingApiMigrationMapper {

  fun toBinaryInitialize(params: InitializeProjectParams, clientName: String, clientVersion: String): BinaryInitializeRequest {
    return BinaryInitializeRequest(
      workspaceRoot = params.directory,
      buildSystem = BuildSystem.GRADLE,
      clientName = clientName,
      clientVersion = clientVersion,
      callerId = "tooling-api-legacy-bridge",
      capabilities = setOf("migration:legacy-tooling-api"),
    )
  }

  fun toCompileRequest(request: ExecutionRequest): BuildTargetCompileRequest {
    return BuildTargetCompileRequest(
      buildId = request.requestId,
      targetIds = if (request.tasks.isEmpty()) listOf(":") else request.tasks,
      arguments = request.arguments + request.jvmArguments,
    )
  }
}
