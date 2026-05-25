package com.zerostudio.tooling.buildgrpc

/**
 * 协议层领域模型：对齐 BSP 初始化/能力协商 + BEP 事件流语义。
 */
data class BuildClientMetadata(
  val clientName: String,
  val clientVersion: String,
  val capabilities: Set<String> = emptySet(),
)

data class BuildServerMetadata(
  val serverName: String,
  val serverVersion: String,
  val supportedLanguages: Set<String>,
  val protocolFeatures: Set<String>,
)

enum class BuildLifecycleState {
  CREATED,
  INITIALIZED,
  RUNNING,
  FINISHED,
  FAILED,
  CANCELLED,
}

data class BuildTargetDescriptor(
  val label: String,
  val language: String? = null,
)

data class BuildExecutionPlan(
  val buildId: String,
  val targets: List<BuildTargetDescriptor>,
  val options: Map<String, String> = emptyMap(),
)
