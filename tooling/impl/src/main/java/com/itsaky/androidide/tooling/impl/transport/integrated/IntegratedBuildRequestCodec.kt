package com.itsaky.androidide.tooling.impl.transport.integrated

import com.itsaky.androidide.tooling.api.messages.ExecutionRequest
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.api.messages.TaskExecutionMessage
import com.zerostudio.tooling.buildgrpc.proto.BuildLogOptions
import com.zerostudio.tooling.buildgrpc.proto.GradleTaskRequest
import com.zerostudio.tooling.buildgrpc.proto.InitializeRequest
import com.zerostudio.tooling.buildgrpc.proto.StartBuildRequest

/**
 * Codec for converting tooling transport requests into build-grpc protobuf payloads.
 */
object IntegratedBuildRequestCodec {

  fun encodeInitialize(params: InitializeProjectParams): ByteArray =
    InitializeRequest.newBuilder()
      .setWorkspaceRoot(params.directory)
      .setClientName("androidide-app")
      .setClientVersion("integrated")
      .build()
      .toByteArray()

  fun encodeTaskExecution(message: TaskExecutionMessage): ByteArray =
    newStartBuildRequest(
      buildId = "task-exec",
      tasks = message.tasks,
      arguments = message.arguments,
      jvmArguments = message.jvmArguments,
    ).toByteArray()

  fun encodeExecution(request: ExecutionRequest): ByteArray =
    newStartBuildRequest(
      buildId = request.requestId,
      tasks = request.tasks,
      arguments = request.arguments,
      jvmArguments = request.jvmArguments,
    ).toByteArray()

  private fun newStartBuildRequest(
    buildId: String,
    tasks: List<String>,
    arguments: List<String>,
    jvmArguments: List<String>,
  ): StartBuildRequest =
    StartBuildRequest.newBuilder()
      .setBuildId(buildId)
      .addAllTargets(tasks)
      .addAllGradleTasks(
        tasks.map { task ->
          GradleTaskRequest.newBuilder()
            .setPath(task)
            .setDisplayName(task)
            .addAllArguments(arguments)
            .addAllJvmArguments(jvmArguments)
            .setSelected(true)
            .build()
        },
      )
      .putAllOptions((arguments + jvmArguments).associateWith { "true" })
      .setLogOptions(
        BuildLogOptions.newBuilder()
          .setStreamStdout(true)
          .setStreamStderr(true)
          .setIncludeGradleLifecycle(true)
          .setIncludeStacktraces(true)
          .setMaxBufferedLines(2_000)
          .build(),
      )
      .build()
}
