package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import kotlinx.coroutines.flow.Flow

/**
 * Extensible backend SPI for multi-build-system support (Gradle/Bazel/LLVM/...)
 * while keeping one unified build-grpc protocol surface.
 */
interface BuildBackend {
  val backendId: String

  suspend fun initialize(request: BuildInit): BuildServerInfo

  fun startBuild(request: BuildStart): Flow<BuildEventEnvelope>

  suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult

  suspend fun shutdown(reason: String): Boolean
}

class BuildBackendRegistry(
  backends: List<BuildBackend>,
) {
  private val byId: Map<String, BuildBackend> = backends.associateBy { it.backendId }

  fun require(backendId: String): BuildBackend =
    byId[backendId] ?: error("Unknown build backend: $backendId")

  fun default(): BuildBackend = byId.values.firstOrNull()
    ?: error("No build backend registered")

  fun ids(): Set<String> = byId.keys
}
