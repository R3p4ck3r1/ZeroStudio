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

import org.gradle.tooling.model.GradleProject
import org.slf4j.LoggerFactory

/**
 * Detects and validates AGP (Android Gradle Plugin) versions.
 *
 * @author Akash Yadav
 */
object AgpVersionChecker {

  private val log = LoggerFactory.getLogger(AgpVersionChecker::class.java)

  /** Supported AGP version ranges. */
  private val supportedRanges = listOf(
    AgpVersionRange.AGP_7X,
    AgpVersionRange.AGP_8X,
    AgpVersionRange.AGP_9X
  )

  /**
   * Detect the AGP version from the given [GradleProject].
   *
   * This method tries multiple strategies to detect the AGP version:
   * 1. From Gradle properties (android.plugin.version)
   * 2. From buildscript classpath
   * 3. From plugin version inference
   *
   * @param gradleProject The Gradle project to detect AGP version from.
   * @return The detected [AgpVersion], or null if detection fails.
   */
  fun detect(gradleProject: GradleProject): AgpVersion? {
    log.debug("Detecting AGP version from Gradle project: {}", gradleProject.name)

    // Strategy 1: Try to read from gradle.properties
    detectFromGradleProperties(gradleProject)?.let { version ->
      log.debug("Detected AGP version {} from gradle.properties", version)
      return version
    }

    // Strategy 2: Try to detect from plugin applied
    detectFromPluginId(gradleProject)?.let { version ->
      log.debug("Detected AGP version {} from plugin ID", version)
      return version
    }

    log.warn("Could not detect AGP version for project: {}", gradleProject.name)
    return null
  }

  /**
   * Detect AGP version from gradle.properties.
   */
  private fun detectFromGradleProperties(gradleProject: GradleProject): AgpVersion? {
    return try {
      val projectDir = gradleProject.projectDirectory
      val propertiesFile = projectDir.file("gradle.properties").asFile
      
      if (propertiesFile.exists()) {
        val properties = java.util.Properties()
        propertiesFile.inputStream().use { properties.load(it) }
        
        // Try android.plugin.version property
        properties.getProperty("android.plugin.version")?.let { version ->
          return AgpVersion.parse(version)
        }
        
        // Try android.build.gradle.version property
        properties.getProperty("android.build.gradle.version")?.let { version ->
          return AgpVersion.parse(version)
        }
      }
      
      // Try to read from project level build.gradle(.kts)
      detectFromBuildScript(gradleProject)
    } catch (e: Exception) {
      log.debug("Failed to detect AGP version from gradle.properties: {}", e.message)
      null
    }
  }

  /**
   * Detect AGP version from the project's build script.
   */
  private fun detectFromBuildScript(gradleProject: GradleProject): AgpVersion? {
    return try {
      val buildFile = gradleProject.projectDirectory.file("build.gradle.kts").asFile
        .takeIf { it.exists() }
        ?: gradleProject.projectDirectory.file("build.gradle").asFile

      if (buildFile.exists()) {
        val content = buildFile.readText()
        
        // Look for android gradle plugin version in buildscript block
        val buildscriptPattern = Regex(
          """buildscript\s*\{[^}]*classpath[^}]*com\.android\.tools\.build:gradle:(.+?)['"]""",
          RegexOption.DOT_MATCHES_ALL
        )
        buildscriptPattern.find(content)?.groupValues?.getOrNull(1)?.let { version ->
          return AgpVersion.parse(version)
        }
        
        // Look for plugins block
        val pluginsPattern = Regex(
          """id\("com\.android\.application"\)\s*version\s*["'](.+?)["']""",
          RegexOption.DOT_MATCHES_ALL
        )
        pluginsPattern.find(content)?.groupValues?.getOrNull(1)?.let { version ->
          return AgpVersion.parse(version)
        }
      }
      null
    } catch (e: Exception) {
      log.debug("Failed to detect AGP version from build script: {}", e.message)
      null
    }
  }

  /**
   * Detect AGP version from applied plugin IDs.
   */
  private fun detectFromPluginId(gradleProject: GradleProject): AgpVersion? {
    return try {
      // Look for android plugin
      gradleProject.plugins.forEach { plugin ->
        val pluginId = plugin.id
        if (pluginId.startsWith("com.android.")) {
          // Try to get version from plugin specification
          // This is a best-effort detection
          log.debug("Found Android plugin: {}", pluginId)
        }
      }
      null
    } catch (e: Exception) {
      log.debug("Failed to detect AGp version from plugin ID: {}", e.message)
      null
    }
  }

  /**
   * Check if the given [AgpVersion] is supported.
   *
   * @param version The version to check.
   * @return True if the version is supported, false otherwise.
   */
  fun isSupported(version: AgpVersion): Boolean {
    return supportedRanges.any { it.contains(version) }
  }

  /**
   * Check if the given version string is supported.
   *
   * @param versionString The version string to check.
   * @return True if the version is supported, false otherwise.
   */
  fun isSupported(versionString: String): Boolean {
    return try {
      isSupported(AgpVersion.parse(versionString))
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Get all supported version ranges.
   *
   * @return A list of supported [AgpVersionRange].
   */
  fun getSupportedRanges(): List<AgpVersionRange> = supportedRanges

  /**
   * Get the version category for the given [AgpVersion].
   *
   * @param version The version to categorize.
   * @return The version category string (e.g., "7.x", "8.x", "9.x").
   */
  fun getVersionCategory(version: AgpVersion): String {
    return when {
      version.major == 7 -> "7.x"
      version.major == 8 -> "8.x"
      version.major >= 9 -> "9.x"
      else -> "unknown"
    }
  }

  /**
   * Validate that the given version is compatible for the tooling API.
   *
   * @param version The version to validate.
   * @return A [ValidationResult] indicating whether the version is valid.
   */
  fun validateForToolingApi(version: AgpVersion): ValidationResult {
    if (!isSupported(version)) {
      return ValidationResult(
        isValid = false,
        message = "AGP version ${version.versionString} is not supported. " +
          "Supported versions are: 7.x, 8.x, 9.x"
      )
    }

    if (version.isPreview) {
      return ValidationResult(
        isValid = true,
        message = "Warning: Using preview version ${version.versionString}. " +
          "This may have stability issues.",
        isWarning = true
      )
    }

    return ValidationResult(isValid = true, message = null)
  }

  /**
   * Represents the result of validating an AGP version.
   */
  data class ValidationResult(
    val isValid: Boolean,
    val message: String?,
    val isWarning: Boolean = false
  )
}
