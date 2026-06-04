package com.itsaky.androidide.services.builder

import android.os.Process
import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.itsaky.androidide.tooling.api.messages.result.BuildCancellationRequestResult
import com.itsaky.androidide.tooling.api.messages.result.ExecutionResult
import com.itsaky.androidide.tooling.api.messages.result.InitializeResult
import com.itsaky.androidide.tooling.api.messages.result.TaskExecutionResult
import com.itsaky.androidide.tooling.api.models.ToolingServerMetadata
import com.itsaky.androidide.tooling.api.transport.ToolingTransportServerEndpoint
import com.itsaky.androidide.tooling.buildgrpc.service.BuildServiceRuntime
import com.itsaky.androidide.tooling.buildgrpc.service.BuildServiceRuntimeFactory
import com.zerostudio.tooling.buildgrpc.RoutingBuildGrpcModule
import com.zerostudio.tooling.buildgrpc.DefaultBinaryBuildServiceApi
import com.zerostudio.tooling.buildgrpc.customapi.ToolingApiBinaryFacade
import java.util.concurrent.CompletableFuture
import org.slf4j.LoggerFactory

/**
 * App-process binary build-service endpoint for the unified build-grpc transport.
 *
 * This endpoint keeps client calls on the transport-neutral Tooling API surface while routing the
 * actual request payloads through the build-grpc binary facade instead of mode-specific
 * client/server adapters.
 */
internal class IntegratedBinaryToolingServerEndpoint : ToolingTransportServerEndpoint {

  private val log = LoggerFactory.getLogger(IntegratedBinaryToolingServerEndpoint::class.java)
  private val routingModule: RoutingBuildGrpcModule by lazy {
    BuildServiceRuntimeFactory.createDefaultRoutingModule()
  }
  private val runtime: BuildServiceRuntime by lazy { createRuntime() }
  private val facade: ToolingApiBinaryFacade by lazy {
    ToolingApiBinaryFacade(DefaultBinaryBuildServiceApi(routingModule))
  }

  override fun metadata(): CompletableFuture<ToolingServerMetadata> =
      CompletableFuture.completedFuture(
          ToolingServerMetadata(
              pid = Process.myPid(),
              toolingApiVersion = "build-grpc",
              supportsPhasedBuildAction = true,
              supportsModelSnapshot = true,
              supportsQueryService = true,
              supportedOperationTypes = setOf("TASK", "PROJECT_CONFIGURATION"),
              negotiatedOperationTypes = setOf("TASK", "PROJECT_CONFIGURATION"),
          )
      )

  override fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult> {
    runtime.start()
    return facade.initialize(params)
  }

  override fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult> {
    runtime.start()
    return facade.executeTasks(message)
  }

  override fun execute(request: ExecutionRequest): CompletableFuture<ExecutionResult> {
    runtime.start()
    return facade.execute(request)
  }

  override fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult> =
      facade.cancelCurrentBuild()

  override fun shutdown(): CompletableFuture<Void> =
      facade.shutdown().whenComplete { _, error ->
        if (error != null) {
          log.warn("Integrated binary build service shutdown returned an error", error)
        }
        runtime.stop()
      }

  private fun createRuntime(): BuildServiceRuntime {
    val runtime =
        BuildServiceRuntimeFactory.create(
            module = routingModule,
            grpcPort = integratedGrpcPort(),
            reapiEndpoint = System.getProperty(PROP_REAPI_ENDPOINT, "").trim(),
            reapiInstanceName = System.getProperty(PROP_REAPI_INSTANCE, "androidide").trim(),
        )
    log.info(
        "Integrated binary build service runtime prepared. grpcPort={}, reapiEndpointConfigured={}",
        integratedGrpcPort(),
        System.getProperty(PROP_REAPI_ENDPOINT, "").isNotBlank(),
    )
    return runtime
  }

  private fun integratedGrpcPort(): Int =
      System.getProperty(PROP_GRPC_PORT, DEFAULT_GRPC_PORT.toString()).toIntOrNull()
          ?: DEFAULT_GRPC_PORT

  private companion object {
    const val DEFAULT_GRPC_PORT = 47920
    const val PROP_GRPC_PORT = "androidide.tooling.integrated.grpc.port"
    const val PROP_REAPI_ENDPOINT = "androidide.tooling.integrated.reapi.endpoint"
    const val PROP_REAPI_INSTANCE = "androidide.tooling.integrated.reapi.instance"
  }
}
