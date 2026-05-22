package com.itsaky.androidide.tooling.impl.transport.reapi

import org.slf4j.LoggerFactory

/**
 * REAPI gateway seam for integrated transport stack.
 *
 * This interface is intentionally transport-neutral at this stage so the project can evolve
 * concrete proto/SDK bindings without reshaping upper-layer gateway contracts.
 */
interface ReapiExecutionGateway {
  fun isEnabled(): Boolean

  fun queryActionResult(actionDigestHash: String, actionDigestSizeBytes: Long): Any?

  fun executeAction(actionDigestHash: String, actionDigestSizeBytes: Long, skipCacheLookup: Boolean): Any?

  fun publishActionResult(actionDigestHash: String, actionDigestSizeBytes: Long, result: Any)

  fun shutdown()
}

/** No-op implementation used before REAPI endpoint configuration is provided. */
class NoOpReapiExecutionGateway : ReapiExecutionGateway {
  override fun isEnabled(): Boolean = false

  override fun queryActionResult(actionDigestHash: String, actionDigestSizeBytes: Long): Any? = null

  override fun executeAction(
      actionDigestHash: String,
      actionDigestSizeBytes: Long,
      skipCacheLookup: Boolean,
  ): Any? = null

  override fun publishActionResult(
      actionDigestHash: String,
      actionDigestSizeBytes: Long,
      result: Any,
  ) = Unit

  override fun shutdown() = Unit
}

/**
 * Placeholder gRPC REAPI gateway.
 *
 * Iteration2 stage: only configuration parsing and logging are enabled; live RPC is deferred to
 * follow-up commits where proto contracts and auth policies are finalized.
 */
class GrpcReapiExecutionGateway(
    private val endpoint: String,
    private val instanceName: String,
) : ReapiExecutionGateway {

  private val log = LoggerFactory.getLogger(GrpcReapiExecutionGateway::class.java)

  override fun isEnabled(): Boolean = endpoint.isNotBlank()

  override fun queryActionResult(actionDigestHash: String, actionDigestSizeBytes: Long): Any? {
    log.debug(
        "REAPI queryActionResult deferred. endpoint={}, instanceName={}, digest={}#{}",
        endpoint,
        instanceName,
        actionDigestHash,
        actionDigestSizeBytes,
    )
    return null
  }

  override fun executeAction(
      actionDigestHash: String,
      actionDigestSizeBytes: Long,
      skipCacheLookup: Boolean,
  ): Any? {
    log.debug(
        "REAPI executeAction deferred. endpoint={}, instanceName={}, digest={}#{}, skipCacheLookup={}",
        endpoint,
        instanceName,
        actionDigestHash,
        actionDigestSizeBytes,
        skipCacheLookup,
    )
    return null
  }

  override fun publishActionResult(
      actionDigestHash: String,
      actionDigestSizeBytes: Long,
      result: Any,
  ) {
    log.debug(
        "REAPI publishActionResult deferred. endpoint={}, instanceName={}, digest={}#{}, resultType={}",
        endpoint,
        instanceName,
        actionDigestHash,
        actionDigestSizeBytes,
        result::class.java.name,
    )
  }

  override fun shutdown() {
    log.debug("REAPI gateway shutdown. endpoint={}, instanceName={}", endpoint, instanceName)
  }
}
