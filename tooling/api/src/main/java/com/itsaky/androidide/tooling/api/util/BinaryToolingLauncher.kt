package com.itsaky.androidide.tooling.api.util

import com.itsaky.androidide.tooling.buildgrpc.bridge.AidlGrpcBridge
import com.itsaky.androidide.tooling.buildgrpc.model.BuildBridgeEvent
import com.zerostudio.tooling.buildgrpc.proto.InitializeRequest
import com.zerostudio.tooling.buildgrpc.proto.StartBuildRequest
import kotlinx.coroutines.flow.Flow

/**
 * Binary-first launcher that routes tooling initialization/build requests
 * through the build-grpc AIDL+gRPC gateway.
 */
class BinaryToolingLauncher(
  private val bridge: AidlGrpcBridge,
) {
  fun initialize(request: InitializeRequest): ByteArray = bridge.initialize(request.toByteArray())

  fun startBuild(request: StartBuildRequest): Flow<BuildBridgeEvent> =
    bridge.startBuild(request.toByteArray())
}
