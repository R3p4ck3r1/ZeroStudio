package com.itsaky.androidide.tooling.api.messages

import java.io.Serializable

data class AndroidInjectedProperties(
    val buildApi: Int? = null,
    val buildAbi: String? = null,
    val studioVersion: String? = null,
) : Serializable {

  fun toGradleArguments(): List<String> {
    val args = mutableListOf<String>()
    buildApi?.let { args.add("-Pandroid.injected.build.api=$it") }
    buildAbi?.takeIf { it.isNotBlank() }?.let { args.add("-Pandroid.injected.build.abi=$it") }
    studioVersion?.takeIf { it.isNotBlank() }?.let {
      args.add("-Pandroid.injected.studio.version=$it")
    }
    return args
  }
}
