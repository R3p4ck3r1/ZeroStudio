/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.models.Versions
import java.io.Serializable

/**
 * Default implementation of the [Versions] interface.
 *
 * Provides fallback support for older AGP versions (8.x and below) that do not provide
 * the Versions model.
 *
 * @author android_zero
 */
class DefaultVersions : Versions, Serializable {

  companion object {
    private const val serialVersionUID = 1L

    /**
     * Create a DefaultVersions instance for AGP 9.x.
     *
     * @param agpVersion The detected AGP version (optional)
     * @return A DefaultVersions instance with appropriate fallback values
     */
    @JvmStatic
    fun forAgp9x(agpVersion: String?): DefaultVersions {
      return DefaultVersions().apply {
        this.agp = agpVersion ?: "9.0.0"
        // Provide basic version map
        this.versions = mutableMapOf(
          Versions.BASIC_ANDROID_PROJECT to DefaultVersionImpl(
            major = 2,
            minor = 0,
            humanReadable = "Basic Android Project v2.0"
          ),
          Versions.ANDROID_PROJECT to DefaultVersionImpl(
            major = 2,
            minor = 0,
            humanReadable = "Android Project v2.0"
          ),
          Versions.ANDROID_DSL to DefaultVersionImpl(
            major = 2,
            minor = 0,
            humanReadable = "Android DSL v2.0"
          ),
          Versions.VARIANT_DEPENDENCIES to DefaultVersionImpl(
            major = 2,
            minor = 0,
            humanReadable = "Variant Dependencies v2.0"
          ),
          Versions.MODEL_PRODUCER to DefaultVersionImpl(
            major = 9,
            minor = 0,
            humanReadable = "AndroidIDE Model Producer v9.0"
          ),
          Versions.MINIMUM_MODEL_CONSUMER to DefaultVersionImpl(
            major = 1,
            minor = 0,
            humanReadable = "Minimum Model Consumer v1.0"
          )
        )
      }
    }

    /**
     * Create a DefaultVersions instance from an actual Versions model.
     *
     * @param versions The Versions model from AGP
     * @return A DefaultVersions instance with values copied from the source
     */
    @JvmStatic
    fun fromVersions(versions: Versions): DefaultVersions {
      return DefaultVersions().apply {
        this.agp = versions.agp
        this.versions = versions.versions.mapValues { (_, v) ->
          DefaultVersionImpl(
            major = v.major,
            minor = v.minor,
            humanReadable = v.humanReadable
          )
        }.toMutableMap()
      }
    }
  }

  override var versions: MutableMap<String, Versions.Version> = mutableMapOf()
  override var agp: String = ""
}

/**
 * Default implementation of [Versions.Version] interface.
 */
class DefaultVersionImpl : Versions.Version, Serializable {

  companion object {
    private const val serialVersionUID = 1L
  }

  override var major: Int = 0
  override var minor: Int = 0
  override var humanReadable: String? = null

  constructor() {}

  constructor(
    major: Int,
    minor: Int,
    humanReadable: String?
  ) {
    this.major = major
    this.minor = minor
    this.humanReadable = humanReadable
  }
}
