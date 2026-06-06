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

package com.itsaky.androidide.tooling.api.util

/**
 * Checker for Android Gradle Plugin (AGP) and Gradle version compatibility.
 *
 * This class provides methods to verify that the combination of AGP and Gradle
 * versions used in a project is compatible and meets the minimum requirements.
 *
 * @author AndroidIDE Team
 */
object AgpGradleVersionChecker {

  /**
   * Result of version compatibility check.
   */
  data class CompatibilityResult(
    val isCompatible: Boolean,
    val agpVersion: String,
    val gradleVersion: String,
    val issues: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
  ) {
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
    val hasIssues: Boolean get() = issues.isNotEmpty()
  }

  /**
   * Minimum required Gradle version for each major AGP version.
   * Based on official Android documentation.
   */
  private val AGP_TO_GRADLE_MIN_VERSION = mapOf(
    "8.0" to "8.0",
    "8.1" to "8.0",
    "8.2" to "8.2",
    "8.3" to "8.4",
    "8.4" to "8.6",
    "8.5" to "8.7",
    "8.6" to "8.7",
    "8.7" to "8.9",
    "9.0" to "9.0",
    "9.1" to "9.1",
    "9.2" to "9.3",
    "9.3" to "9.5"
  )

  /**
   * Maximum recommended Gradle version for each major AGP version.
   */
  private val AGP_TO_GRADLE_MAX_VERSION = mapOf(
    "8.0" to "8.5",
    "8.1" to "8.6",
    "8.2" to "8.7",
    "8.3" to "8.9",
    "8.4" to "8.10",
    "8.5" to "8.11",
    "8.6" to "8.12",
    "8.7" to "8.13",
    "9.0" to "9.5",
    "9.1" to "9.6",
    "9.2" to "9.7",
    "9.3" to "9.9"
  )

  /**
   * Minimum supported AGP version.
   */
  const val MIN_SUPPORTED_AGP_VERSION = "7.0.0"

  /**
   * Minimum supported Gradle version.
   */
  const val MIN_SUPPORTED_GRADLE_VERSION = "7.0"

  /**
   * Check if the given AGP and Gradle versions are compatible.
   *
   * @param agpVersion The Android Gradle Plugin version (e.g., "8.2.0", "9.3.0-alpha06")
   * @param gradleVersion The Gradle version (e.g., "8.4", "9.5.1")
   * @return The compatibility check result
   */
  fun checkCompatibility(agpVersion: String, gradleVersion: String): CompatibilityResult {
    val issues = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    // Clean version strings by removing suffixes like -alpha06, -beta01, -rc1, etc.
    val cleanAgpVersion = cleanVersion(agpVersion)
    val cleanGradleVersion = cleanVersion(gradleVersion)

    // Check minimum versions
    if (compareSemanticVersions(cleanAgpVersion, MIN_SUPPORTED_AGP_VERSION) < 0) {
      issues.add("AGP version $agpVersion is below minimum supported version $MIN_SUPPORTED_AGP_VERSION")
    }

    if (compareSemanticVersions(cleanGradleVersion, MIN_SUPPORTED_GRADLE_VERSION) < 0) {
      issues.add("Gradle version $gradleVersion is below minimum supported version $MIN_SUPPORTED_GRADLE_VERSION")
    }

    // Get AGP major.minor version for lookup
    val agpMajorMinor = getMajorMinorVersion(cleanAgpVersion)
    val gradleMajorMinor = getMajorMinorVersion(cleanGradleVersion)

    // Check minimum required Gradle version for this AGP version
    val minGradleVersion = AGP_TO_GRADLE_MIN_VERSION[agpMajorMinor]
    if (minGradleVersion != null) {
      if (compareSemanticVersions(cleanGradleVersion, minGradleVersion) < 0) {
        issues.add("AGP $agpVersion requires Gradle $minGradleVersion or higher, but using $gradleVersion")
      }
    } else {
      // Unknown AGP version, add warning
      warnings.add("AGP version $agpVersion is not in the known compatibility matrix. Compatibility cannot be guaranteed.")
    }

    // Check if Gradle version is newer than recommended
    val maxGradleVersion = AGP_TO_GRADLE_MAX_VERSION[agpMajorMinor]
    if (maxGradleVersion != null && compareSemanticVersions(cleanGradleVersion, maxGradleVersion) > 0) {
      warnings.add("Gradle $gradleVersion is newer than recommended version $maxGradleVersion for AGP $agpVersion. Some features may not work correctly.")
    }

    return CompatibilityResult(
      isCompatible = issues.isEmpty(),
      agpVersion = agpVersion,
      gradleVersion = gradleVersion,
      issues = issues,
      warnings = warnings
    )
  }

  /**
   * Get the recommended Gradle version for a given AGP version.
   *
   * @param agpVersion The Android Gradle Plugin version
   * @return The recommended Gradle version, or null if unknown
   */
  fun getRecommendedGradleVersion(agpVersion: String): String? {
    val cleanAgpVersion = cleanVersion(agpVersion)
    val agpMajorMinor = getMajorMinorVersion(cleanAgpVersion)
    return AGP_TO_GRADLE_MIN_VERSION[agpMajorMinor]
  }

  /**
   * Clean version string by removing pre-release suffixes.
   */
  private fun cleanVersion(version: String): String {
    val dashIndex = version.indexOf('-')
    if (dashIndex > 0) {
      return version.substring(0, dashIndex)
    }
    return version
  }

  /**
   * Get major.minor version from a full semantic version.
   */
  private fun getMajorMinorVersion(version: String): String {
    val parts = version.split(".")
    return if (parts.size >= 2) {
      "${parts[0]}.${parts[1]}"
    } else {
      version
    }
  }

  /**
   * Check if the given AGP version is AGP 9.x or newer.
   */
  fun isAgp9xOrNewer(agpVersion: String): Boolean {
    val cleanAgpVersion = cleanVersion(agpVersion)
    return compareSemanticVersions(cleanAgpVersion, "9.0.0") >= 0
  }

  /**
   * Check if the given Gradle version is Gradle 9.x or newer.
   */
  fun isGradle9xOrNewer(gradleVersion: String): Boolean {
    val cleanGradleVersion = cleanVersion(gradleVersion)
    return compareSemanticVersions(cleanGradleVersion, "9.0") >= 0
  }
}
