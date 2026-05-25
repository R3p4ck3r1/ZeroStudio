package com.zerostudio.tooling.buildgrpc

import com.zerostudio.tooling.buildgrpc.proto.BuildEventEnvelope
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/**
 * High-level orchestrator that routes builds to pluggable backends.
 */
class RoutingBuildGrpcModule(
  private val backendRegistry: BuildBackendRegistry,
) : BuildGrpcModule {

  private val backendByBuildId = ConcurrentHashMap<String, BuildBackend>()
  private var sessionContext: BuildSessionContext? = null

  override suspend fun initialize(request: BuildInit): BuildServerInfo {
    sessionContext = BuildSessionContextResolver.fromInit(request)
    val backend = resolveBackendFromContextOrDefault()
    val info = backend.initialize(request)
    return info.copy(
      protocolFeatures = (info.protocolFeatures +
        listOf("routing.backends:${backendRegistry.ids().sorted().joinToString(",")}")).distinct(),
    )
  }

  override fun startBuild(request: BuildStart): Flow<BuildEventEnvelope> {
    val backend = selectBackend(request)
    backendByBuildId[request.buildId] = backend
    return backend.startBuild(request)
  }

  override suspend fun shutdown(reason: String): Boolean {
    val grouped = backendByBuildId.values.toSet()
    backendByBuildId.clear()
    return grouped.all { it.shutdown(reason) }
  }

  suspend fun executeAction(request: ActionExecutionRequest): ActionExecutionResult {
    val backend = backendByBuildId[request.buildId] ?: backendRegistry.default()
    return backend.executeAction(request)
  }

  private fun selectBackend(request: BuildStart): BuildBackend {
    val hinted = request.options[BACKEND_OPTION_KEY]
    if (hinted != null) {
      return backendRegistry.require(hinted)
    }
    return resolveBackendFromContextOrDefault()
  }

  private fun resolveBackendFromContextOrDefault(): BuildBackend {
    val hinted = sessionContext?.backendHint
    return if (hinted.isNullOrBlank()) backendRegistry.default() else backendRegistry.require(hinted)
  }

  companion object {
    const val BACKEND_OPTION_KEY = "build.backend"
  }
}
