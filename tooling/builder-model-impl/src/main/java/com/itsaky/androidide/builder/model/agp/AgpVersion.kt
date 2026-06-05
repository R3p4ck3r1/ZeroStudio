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
package com.itsaky.androidide.builder.model.agp

import java.io.Serializable

/**
 * Represents a version of the Android Gradle Plugin (AGP).
 *
 * @author Akash Yadav
 */
data class AgpVersion(
  val major: Int,
  val minor: Int,
  val patch: Int,
  val isPreview: Boolean = false,
  val previewType: String? = null,
  val buildTimestamp: String? = null
) : Comparable<AgpVersion>, Serializable {

  private val serialVersionUID = 1L

  /** Returns the version string in the format "major.minor.patch" or "major.minor.patch-previewType" */
  val versionString: String
    get() = buildString {
      append("$major.$minor.$patch")
      if (isPreview && previewType != null) {
        append("-$previewType")
      }
    }

  /** Returns the version code suitable for comparison. */
  val versionCode: Long
    get() {
      val previewMultiplier = if (isPreview) 0 else 1
      return (major * 1000000L) + (minor * 1000L) + (patch * 10L) + previewMultiplier
    }

  companion object {
    /** Parse an AGP version string into an [AgpVersion]. */
    fun parse(versionString: String): AgpVersion {
      val cleaned = versionString.trim()
      
      // Handle preview versions like "9.0.0-alpha06", "8.2.0-beta01"
      val previewRegex = Regex("^(.+?)-(.+)$")
      val previewMatch = previewRegex.matchEntire(cleaned)
      
      val baseVersion: String
      val previewType: String?
      
      if (previewMatch != null) {
        baseVersion = previewMatch.groupValues[1]
        previewType = previewMatch.groupValues[2]
      } else {
        baseVersion = cleaned
        previewType = null
      }

      val parts = baseVersion.split(".")
      val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
      val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
      val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
      
      val isPreview = previewType != null

      return AgpVersion(
        major = major,
        minor = minor,
        patch = patch,
        isPreview = isPreview,
        previewType = previewType
      )
    }

    /** Returns the minimum supported AGP version (7.0). */
    fun minSupported(): AgpVersion = parse("7.0.0")

    /** Returns the maximum supported AGP version (9.3). */
    fun maxSupported(): AgpVersion = parse("9.3.99")

    /** Check if the given version string is supported. */
    fun isVersionSupported(versionString: String): Boolean {
      return try {
        isVersionSupported(parse(versionString))
      } catch (e: Exception) {
        false
      }
    }

    /** Check if the given [AgpVersion] is supported. */
    fun isVersionSupported(version: AgpVersion): Boolean {
      return version >= minSupported() && version <= maxSupported()
    }
  }

  override fun compareTo(other: AgpVersion): Int {
    return this.versionCode.compareTo(other.versionCode)
  }

  override fun toString(): String = versionString
}

/**
 * Represents a range of AGP versions.
 */
data class AgpVersionRange(
  val minMajor: Int,
  val minMinor: Int,
  val minPatch: Int,
  val maxMajor: Int,
  val maxMinor: Int,
  val maxPatch: Int
) : Serializable {

  private val serialVersionUID = 1L

  /** Check if the given [AgpVersion] is within this range. */
  fun contains(version: AgpVersion): Boolean {
    val min = AgpVersion(minMajor, minMinor, minPatch)
    val max = AgpVersion(maxMajor, maxMinor, maxPatch)
    return version >= min && version <= max
  }

  /** Check if the given version string is within this range. */
  fun contains(versionString: String): Boolean {
    return contains(AgpVersion.parse(versionString))
  }

  companion object {
    /** AGP 7.x range (7.0 - 7.4). */
    val AGP_7X = AgpVersionRange(7, 0, 0, 7, 4, 99)

    /** AGP 8.x range (8.0 - 8.7). */
    val AGP_8X = AgpVersionRange(8, 0, 0, 8, 7, 99)

    /** AGP 9.x range (9.0 - 9.3). */
    val AGP_9X = AgpVersionRange(9, 0, 0, 9, 3, 99)

    /** All supported ranges. */
    val ALL_SUPPORTED = listOf(AGP_7X, AGP_8X, AGP_9X)
  }
}
