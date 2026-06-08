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
package com.itsaky.androidide.tooling.impl

import org.slf4j.LoggerFactory

/**
 * Detects the type of a Gradle project.
 *
 * Note: The Gradle Tooling API does not expose plugin information directly.
 * Project type detection is handled by checking if a project has Android models.
 *
 * @author Akash Yadav
 */
object ProjectTypeDetector {

  private val log = LoggerFactory.getLogger(ProjectTypeDetector::class.java)

  /**
   * Represents the detected project category.
   */
  enum class ProjectCategory {
    // Android project types
    ANDROID_APP,
    ANDROID_LIBRARY,
    ANDROID_FEATURE,
    ANDROID_DYNAMIC_FEATURE,

    // Non-Android Gradle project types
    SPRING_BOOT_APPLICATION,
    SPRING_BOOT_LIBRARY,
    KOTLIN_JVM_APPLICATION,
    KOTLIN_JVM_LIBRARY,
    JAVA_APPLICATION,
    JAVA_LIBRARY,
    GRADLE_PLUGIN,

    // Fallback
    UNKNOWN
  }

  /**
   * Result of project type detection.
   */
  data class DetectionResult(
    val category: ProjectCategory,
    val projectType: String,
    val isAndroidProject: Boolean,
    val isPreview: Boolean,
    val plugins: List<String>,
    val buildFile: String?,
    val detectedVersion: String?
  ) {
    companion object {
      fun unknown() = DetectionResult(
        category = ProjectCategory.UNKNOWN,
        projectType = "Unknown",
        isAndroidProject = false,
        isPreview = false,
        plugins = emptyList(),
        buildFile = null,
        detectedVersion = null
      )
    }
  }

  /**
   * Check if the project is an Android project.
   *
   * Note: Actual Android project detection is handled by checking if BasicAndroidProject
   * model is available through the BuildController.
   */
  fun isAndroidProject(): Boolean {
    return false
  }

  /**
   * Get a human-readable description of the project category.
   */
  fun getCategoryDescription(category: ProjectCategory): String {
    return when (category) {
      ProjectCategory.ANDROID_APP -> "Android Application"
      ProjectCategory.ANDROID_LIBRARY -> "Android Library"
      ProjectCategory.ANDROID_FEATURE -> "Android Feature Module"
      ProjectCategory.ANDROID_DYNAMIC_FEATURE -> "Android Dynamic Feature Module"
      ProjectCategory.SPRING_BOOT_APPLICATION -> "Spring Boot Application"
      ProjectCategory.SPRING_BOOT_LIBRARY -> "Spring Boot Library"
      ProjectCategory.KOTLIN_JVM_APPLICATION -> "Kotlin JVM Application"
      ProjectCategory.KOTLIN_JVM_LIBRARY -> "Kotlin JVM Library"
      ProjectCategory.JAVA_APPLICATION -> "Java Application"
      ProjectCategory.JAVA_LIBRARY -> "Java Library"
      ProjectCategory.GRADLE_PLUGIN -> "Gradle Plugin"
      ProjectCategory.UNKNOWN -> "Unknown Gradle Project"
    }
  }
}
