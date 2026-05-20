package com.itsaky.androidide.tooling.api.models

import java.io.Serializable

data class AndroidProjectFlagsModel(
    val values: Map<String, Boolean?>,
) : Serializable {

  fun getOrLegacyDefault(flagName: String, legacyDefault: Boolean): Boolean {
    return values[flagName] ?: legacyDefault
  }
}
