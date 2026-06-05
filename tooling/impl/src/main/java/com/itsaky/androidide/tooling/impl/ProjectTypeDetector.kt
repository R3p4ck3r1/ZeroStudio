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

import org.gradle.tooling.model.GradleProject
import org.slf4j.LoggerFactory

/**
 * Detects the type of a Gradle project.
 *
 * This detector can identify:
 * - Android projects (app, library, feature, dynamic feature)
 * - Spring Boot projects
 * - Kotlin JVM projects (application, library)
 * - Java projects (application, library)
 * - Gradle plugin projects
 * - Unknown/standard Gradle projects
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
   * Detect the project type from the given [GradleProject].
   *
   * @param gradleProject The Gradle project to analyze.
   * @return A [DetectionResult] containing the detected project type.
   */
  fun detect(gradleProject: GradleProject): DetectionResult {
    log.debug("Detecting project type for: {}", gradleProject.name)

    val plugins = detectPlugins(gradleProject)
    log.debug("Detected plugins: {}", plugins)

    // Check for Android projects first
    if (plugins.any { it.startsWith("com.android.") }) {
      return detectAndroidProjectType(gradleProject, plugins)
    }

    // Check for Spring Boot
    if (plugins.any { it == "org.springframework.boot" }) {
      return detectSpringBootType(gradleProject, plugins)
    }

    // Check for Kotlin JVM
    if (plugins.any { it == "org.jetbrains.kotlin.jvm" || it == "kotlin(" }) {
      return detectKotlinJvmType(gradleProject, plugins)
    }

    // Check for Java
    if (plugins.any { it == "java" || it == "java-library" }) {
      return detectJavaType(gradleProject, plugins)
    }

    // Check for Gradle plugin
    if (plugins.any { it == "gradle-plugin" || it == "com.github.johnrengelman.plugin" }) {
      return DetectionResult(
        category = ProjectCategory.GRADLE_PLUGIN,
        projectType = "Gradle Plugin",
        isAndroidProject = false,
        isPreview = false,
        plugins = plugins,
        buildFile = detectBuildFile(gradleProject),
        detectedVersion = null
      )
    }

    log.debug("Could not determine specific project type, using UNKNOWN")
    return DetectionResult.unknown()
  }

  /**
   * Detect the specific Android project type.
   */
  private fun detectAndroidProjectType(
    gradleProject: GradleProject,
    plugins: List<String>
  ): DetectionResult {
    val projectType = when {
      plugins.contains("com.android.application") -> ProjectCategory.ANDROID_APP
      plugins.contains("com.android.library") -> ProjectCategory.ANDROID_LIBRARY
      plugins.contains("com.android.feature") -> ProjectCategory.ANDROID_FEATURE
      plugins.contains("com.android.dynamic-feature") -> ProjectCategory.ANDROID_DYNAMIC_FEATURE
      else -> ProjectCategory.ANDROID_APP // Default to app
    }

    val displayName = when (projectType) {
      ProjectCategory.ANDROID_APP -> "Android App"
      ProjectCategory.ANDROID_LIBRARY -> "Android Library"
      ProjectCategory.ANDROID_FEATURE -> "Android Feature"
      ProjectCategory.ANDROID_DYNAMIC_FEATURE -> "Android Dynamic Feature"
      else -> "Android"
    }

    // Try to detect AGP version from build file
    val agpVersion = detectAgpVersion(gradleProject)

    return DetectionResult(
      category = projectType,
      projectType = displayName,
      isAndroidProject = true,
      isPreview = agpVersion?.contains("alpha") == true || agpVersion?.contains("beta") == true,
      plugins = plugins,
      buildFile = detectBuildFile(gradleProject),
      detectedVersion = agpVersion
    )
  }

  /**
   * Detect AGP version from build file.
   */
  private fun detectAgpVersion(gradleProject: GradleProject): String? {
    return try {
      val buildFile = gradleProject.projectDirectory.file("build.gradle.kts").asFile
        .takeIf { it.exists() }
        ?: gradleProject.projectDirectory.file("build.gradle").asFile
          .takeIf { it.exists() }
          ?: return null

      val content = buildFile.readText()

      // Look for android gradle plugin version in plugins block
      val pluginsPattern = Regex(
        """id\(["']com\.android\.(application|library|feature|dynamic-feature)["']\)\s*version\s*["'](.+?)["']"""
      )
      pluginsPattern.find(content)?.groupValues?.getOrNull(2)?.let { return it }

      // Look in buildscript classpath
      val buildscriptPattern = Regex(
        """classpath\s+["']com\.android\.tools\.build:gradle:(.+?)["']"""
      )
      buildscriptPattern.find(content)?.groupValues?.getOrNull(1)
    } catch (e: Exception) {
      log.debug("Failed to detect AGP version: {}", e.message)
      null
    }
  }

  /**
   * Detect the specific Spring Boot project type.
   */
  private fun detectSpringBootType(
    gradleProject: GradleProject,
    plugins: List<String>
  ): DetectionResult {
    val projectType = when {
      plugins.contains("org.springframework.boot") && plugins.contains("java") -> ProjectCategory.SPRING_BOOT_APPLICATION
      plugins.contains("org.springframework.boot") && plugins.contains("kotlin") -> ProjectCategory.SPRING_BOOT_APPLICATION
      else -> ProjectCategory.SPRING_BOOT_LIBRARY
    }

    val displayName = when (projectType) {
      ProjectCategory.SPRING_BOOT_APPLICATION -> "Spring Boot Application"
      ProjectCategory.SPRING_BOOT_LIBRARY -> "Spring Boot Library"
      else -> "Spring Boot"
    }

    // Try to detect Spring Boot version from build file
    val springBootVersion = detectSpringBootVersion(gradleProject)

    return DetectionResult(
      category = projectType,
      projectType = displayName,
      isAndroidProject = false,
      isPreview = false,
      plugins = plugins,
      buildFile = detectBuildFile(gradleProject),
      detectedVersion = springBootVersion
    )
  }

  /**
   * Detect the specific Kotlin JVM project type.
   */
  private fun detectKotlinJvmType(
    gradleProject: GradleProject,
    plugins: List<String>
  ): DetectionResult {
    val projectType = when {
      plugins.contains("org.jetbrains.kotlin.jvm.application") -> ProjectCategory.KOTLIN_JVM_APPLICATION
      plugins.contains("org.jetbrains.kotlin.jvm.library") -> ProjectCategory.KOTLIN_JVM_LIBRARY
      plugins.contains("kotlin(") && plugins.contains("jvm") -> ProjectCategory.KOTLIN_JVM_APPLICATION
      else -> ProjectCategory.KOTLIN_JVM_LIBRARY
    }

    val displayName = when (projectType) {
      ProjectCategory.KOTLIN_JVM_APPLICATION -> "Kotlin JVM Application"
      ProjectCategory.KOTLIN_JVM_LIBRARY -> "Kotlin JVM Library"
      else -> "Kotlin JVM"
    }

    // Try to detect Kotlin version
    val kotlinVersion = detectKotlinVersion(gradleProject)

    return DetectionResult(
      category = projectType,
      projectType = displayName,
      isAndroidProject = false,
      isPreview = false,
      plugins = plugins,
      buildFile = detectBuildFile(gradleProject),
      detectedVersion = kotlinVersion
    )
  }

  /**
   * Detect the specific Java project type.
   */
  private fun detectJavaType(
    gradleProject: GradleProject,
    plugins: List<String>
  ): DetectionResult {
    val projectType = when {
      plugins.contains("java-application") || plugins.contains("application") -> ProjectCategory.JAVA_APPLICATION
      plugins.contains("java-library") -> ProjectCategory.JAVA_LIBRARY
      plugins.contains("java") -> ProjectCategory.JAVA_APPLICATION
      else -> ProjectCategory.JAVA_LIBRARY
    }

    val displayName = when (projectType) {
      ProjectCategory.JAVA_APPLICATION -> "Java Application"
      ProjectCategory.JAVA_LIBRARY -> "Java Library"
      else -> "Java"
    }

    // Try to detect Java version
    val javaVersion = detectJavaVersion(gradleProject)

    return DetectionResult(
      category = projectType,
      projectType = displayName,
      isAndroidProject = false,
      isPreview = false,
      plugins = plugins,
      buildFile = detectBuildFile(gradleProject),
      detectedVersion = javaVersion
    )
  }

  /**
   * Detect plugins applied to the project.
   */
  private fun detectPlugins(gradleProject: GradleProject): List<String> {
    val plugins = mutableListOf<String>()

    try {
      for (plugin in gradleProject.plugins) {
        plugins.add(plugin.id)
      }
    } catch (e: Exception) {
      log.debug("Failed to detect plugins from GradleProject: {}", e.message)
    }

    // Also check build file for plugins block
    detectPluginsFromBuildFile(gradleProject)?.let { plugins.addAll(it) }

    return plugins.distinct()
  }

  /**
   * Detect plugins from the build file content.
   */
  private fun detectPluginsFromBuildFile(gradleProject: GradleProject): List<String>? {
    return try {
      val buildFile = gradleProject.projectDirectory.file("build.gradle.kts").asFile
        .takeIf { it.exists() }
        ?: gradleProject.projectDirectory.file("build.gradle").asFile
          .takeIf { it.exists() }
          ?: return null

      val content = buildFile.readText()
      val plugins = mutableListOf<String>()

      // Pattern for plugins block: id("plugin.id") version "x.x.x"
      val pluginsBlockPattern = Regex(
        """plugins\s*\{[^}]*id\(["'](.+?)["'\)]"""
      )

      pluginsBlockPattern.findAll(content).forEach { match ->
        plugins.add(match.groupValues[1])
      }

      // Pattern for buildscript block
      val buildscriptPattern = Regex(
        """buildscript\s*\{[^}]*classpath\s+["'](.+?)["']"""
      )

      buildscriptPattern.findAll(content).forEach { match ->
        plugins.add(match.groupValues[1])
      }

      if (plugins.isEmpty()) null else plugins
    } catch (e: Exception) {
      log.debug("Failed to detect plugins from build file: {}", e.message)
      null
    }
  }

  /**
   * Detect the build file type.
   */
  private fun detectBuildFile(gradleProject: GradleProject): String? {
    return when {
      gradleProject.projectDirectory.file("build.gradle.kts").asFile.exists() -> "Kotlin DSL (build.gradle.kts)"
      gradleProject.projectDirectory.file("build.gradle").asFile.exists() -> "Groovy DSL (build.gradle)"
      gradleProject.projectDirectory.file("build.gradle.rb").asFile.exists() -> "Ruby DSL (build.gradle.rb)"
      else -> null
    }
  }

  /**
   * Detect Spring Boot version from build file.
   */
  private fun detectSpringBootVersion(gradleProject: GradleProject): String? {
    return try {
      val buildFile = gradleProject.projectDirectory.file("build.gradle.kts").asFile
        .takeIf { it.exists() }
        ?: gradleProject.projectDirectory.file("build.gradle").asFile
          .takeIf { it.exists() }
          ?: return null

      val content = buildFile.readText()

      // Look for springBoot version
      val springBootPattern = Regex(
        """id\(["']org\.springframework\.boot["']\)\s*version\s*["'](.+?)["']"""
      )

      springBootPattern.find(content)?.groupValues?.getOrNull(1)?.let { return it }

      // Look for version catalog or dependencyManagement
      val managementPattern = Regex(
        """springBoot\s+["'](.+?)["']"""
      )

      managementPattern.find(content)?.groupValues?.getOrNull(1)
    } catch (e: Exception) {
      log.debug("Failed to detect Spring Boot version: {}", e.message)
      null
    }
  }

  /**
   * Detect Kotlin version from build file.
   */
  private fun detectKotlinVersion(gradleProject: GradleProject): String? {
    return try {
      val buildFile = gradleProject.projectDirectory.file("build.gradle.kts").asFile
        .takeIf { it.exists() }
        ?: gradleProject.projectDirectory.file("build.gradle").asFile
          .takeIf { it.exists() }
          ?: return null

      val content = buildFile.readText()

      // Look for kotlin version in plugins block
      val kotlinPattern = Regex(
        """id\(["']org\.jetbrains\.kotlin\.["']?\)\s*version\s*["'](.+?)["']"""
      )

      kotlinPattern.find(content)?.groupValues?.getOrNull(1)
    } catch (e: Exception) {
      log.debug("Failed to detect Kotlin version: {}", e.message)
      null
    }
  }

  /**
   * Detect Java version from build file or project.
   */
  private fun detectJavaVersion(gradleProject: GradleProject): String? {
    return try {
      val buildFile = gradleProject.projectDirectory.file("build.gradle.kts").asFile
        .takeIf { it.exists() }
        ?: gradleProject.projectDirectory.file("build.gradle").asFile
          .takeIf { it.exists() }
          ?: return null

      val content = buildFile.readText()

      // Look for java plugin with toolchain or sourceCompatibility
      val javaPattern = Regex(
        """sourceCompatibility\s*=\?\s*["'](.+?)["']"""
      )

      javaPattern.find(content)?.groupValues?.getOrNull(1)
        ?: try {
          gradleProject.plugins.find { it.id == "java" }?.let { "Java" }
        } catch (e: Exception) {
          null
        }
    } catch (e: Exception) {
      log.debug("Failed to detect Java version: {}", e.message)
      null
    }
  }

  /**
   * Check if the project is an Android project.
   */
  fun isAndroidProject(gradleProject: GradleProject): Boolean {
    return detectPlugins(gradleProject).any { it.startsWith("com.android.") }
  }

  /**
   * Check if the project uses Kotlin DSL.
   */
  fun isKotlinDsl(gradleProject: GradleProject): Boolean {
    return gradleProject.projectDirectory.file("build.gradle.kts").asFile.exists()
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
