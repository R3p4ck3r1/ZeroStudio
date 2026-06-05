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
package com.itsaky.androidide.tooling.impl.internal

import com.itsaky.androidide.tooling.api.IGradleProject
import com.itsaky.androidide.tooling.api.ProjectType
import com.itsaky.androidide.tooling.api.models.GradleBuildEnvironment
import com.itsaky.androidide.tooling.api.models.GradleBuildMetadata
import com.itsaky.androidide.tooling.api.models.GradleJavaEnvironment
import com.itsaky.androidide.tooling.api.models.GradleRuntimeEnvironment
import com.itsaky.androidide.tooling.api.models.GradleTask
import com.itsaky.androidide.tooling.api.models.ProjectMetadata
import com.itsaky.androidide.tooling.impl.ProjectTypeDetector
import java.io.Serializable
import java.util.concurrent.CompletableFuture
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.gradle.GradleBuild

/**
 * Implementation of [IGradleProject] for non-Android Gradle projects.
 *
 * This includes Spring Boot, Kotlin JVM, Java, and generic Gradle projects.
 *
 * @author Akash Yadav
 */
internal class NonAndroidProjectImpl(
    gradleProject: GradleProject,
    private val buildEnvironment: GradleBuildEnvironment? = null,
    private val gradleBuild: GradleBuildMetadata? = null,
    private val projectCategory: ProjectTypeDetector.ProjectCategory = ProjectTypeDetector.ProjectCategory.UNKNOWN,
    private val projectType: String = "Unknown",
    private val frameworkVersion: String? = null,
    private val mainClass: String? = null,
    private val additionalMetadata: Map<String, String?> = emptyMap()
) : GradleProjectImpl(gradleProject, buildEnvironment, gradleBuild), IGradleProject, Serializable {

  private val serialVersionUID = 1L

  override fun getMetadata(): CompletableFuture<ProjectMetadata> {
    return CompletableFuture.supplyAsync {
      ProjectMetadata(
        name = gradleProject.name,
        path = gradleProject.path,
        projectDirectory = gradleProject.projectDirectory,
        buildDirectory = gradleProject.buildDirectory,
        description = gradleProject.description,
        buildScript = gradleProject.buildScript.sourceFile,
        projectType = mapCategoryToProjectType()
      )
    }
  }

  override fun getTasks(): CompletableFuture<List<GradleTask>> {
    return CompletableFuture.supplyAsync {
      mutableListOf<GradleTask>().apply {
        for (task in gradleProject.tasks) {
          add(
            GradleTask(
              name = task.name,
              description = task.description,
              group = task.group,
              path = task.path,
              displayName = task.displayName,
              isPublic = task.isPublic,
              project = task.project.path
            )
          )
        }
      }
    }
  }

  /**
   * Map the project category to a standard ProjectType.
   */
  private fun mapCategoryToProjectType(): ProjectType {
    return when (projectCategory) {
      ProjectTypeDetector.ProjectCategory.SPRING_BOOT_APPLICATION,
      ProjectTypeDetector.ProjectCategory.SPRING_BOOT_LIBRARY -> ProjectType.SpringBoot
      ProjectTypeDetector.ProjectCategory.KOTLIN_JVM_APPLICATION,
      ProjectTypeDetector.ProjectCategory.KOTLIN_JVM_LIBRARY -> ProjectType.KotlinJvm
      ProjectTypeDetector.ProjectCategory.JAVA_APPLICATION,
      ProjectTypeDetector.ProjectCategory.JAVA_LIBRARY -> ProjectType.Java
      ProjectTypeDetector.ProjectCategory.GRADLE_PLUGIN -> ProjectType.GradlePlugin
      else -> ProjectType.Gradle
    }
  }

  companion object {

    /**
     * Create a NonAndroidProjectImpl from detection result.
     */
    fun from(
      gradleProject: GradleProject,
      buildEnvironment: BuildEnvironment?,
      gradleBuild: GradleBuild?,
      projectCategory: ProjectTypeDetector.ProjectCategory,
      detectionResult: ProjectTypeDetector.DetectionResult
    ): NonAndroidProjectImpl {
      return NonAndroidProjectImpl(
        gradleProject = gradleProject,
        buildEnvironment = buildEnvironment?.toMetadata(),
        gradleBuild = gradleBuild?.toMetadata(),
        projectCategory = projectCategory,
        projectType = detectionResult.projectType,
        frameworkVersion = detectionResult.detectedVersion,
        additionalMetadata = mapOf(
          "plugins" to detectionResult.plugins.joinToString(","),
          "buildFile" to detectionResult.buildFile,
          "isPreview" to detectionResult.isPreview.toString()
        )
      )
    }

    /**
     * Create a Spring Boot project model.
     */
    fun createSpringBoot(
      gradleProject: GradleProject,
      buildEnvironment: BuildEnvironment?,
      gradleBuild: GradleBuild?,
      springBootVersion: String?,
      mainClass: String?
    ): NonAndroidProjectImpl {
      return NonAndroidProjectImpl(
        gradleProject = gradleProject,
        buildEnvironment = buildEnvironment?.toMetadata(),
        gradleBuild = gradleBuild?.toMetadata(),
        projectCategory = ProjectTypeDetector.ProjectCategory.SPRING_BOOT_APPLICATION,
        projectType = "Spring Boot Application",
        frameworkVersion = springBootVersion,
        mainClass = mainClass,
        additionalMetadata = mapOf(
          "springBootVersion" to springBootVersion,
          "mainClass" to mainClass
        )
      )
    }

    /**
     * Create a Kotlin JVM project model.
     */
    fun createKotlinJvm(
      gradleProject: GradleProject,
      buildEnvironment: BuildEnvironment?,
      gradleBuild: GradleBuild?,
      kotlinVersion: String?,
      jvmTarget: String?
    ): NonAndroidProjectImpl {
      return NonAndroidProjectImpl(
        gradleProject = gradleProject,
        buildEnvironment = buildEnvironment?.toMetadata(),
        gradleBuild = gradleBuild?.toMetadata(),
        projectCategory = ProjectTypeDetector.ProjectCategory.KOTLIN_JVM_APPLICATION,
        projectType = "Kotlin JVM Application",
        frameworkVersion = kotlinVersion,
        additionalMetadata = mapOf(
          "kotlinVersion" to kotlinVersion,
          "jvmTarget" to jvmTarget
        )
      )
    }

    /**
     * Create a Java project model.
     */
    fun createJava(
      gradleProject: GradleProject,
      buildEnvironment: BuildEnvironment?,
      gradleBuild: GradleBuild?,
      javaVersion: String?
    ): NonAndroidProjectImpl {
      return NonAndroidProjectImpl(
        gradleProject = gradleProject,
        buildEnvironment = buildEnvironment?.toMetadata(),
        gradleBuild = gradleBuild?.toMetadata(),
        projectCategory = ProjectTypeDetector.ProjectCategory.JAVA_APPLICATION,
        projectType = "Java Application",
        frameworkVersion = javaVersion,
        additionalMetadata = mapOf(
          "javaVersion" to javaVersion
        )
      )
    }

    /**
     * Convert BuildEnvironment to GradleBuildEnvironment.
     */
    private fun BuildEnvironment.toMetadata(): GradleBuildEnvironment {
      val javaEnvironment =
        try {
          java?.let { GradleJavaEnvironment(it.javaHome, it.jvmArguments.orEmpty()) }
        } catch (_: UnsupportedMethodException) {
          null
        }
      val versionInfo =
        try {
          versionInfo
        } catch (_: UnsupportedMethodException) {
          null
        }

      return GradleBuildEnvironment(
        buildIdentifier = buildIdentifier?.rootDir,
        gradle = gradle?.let { GradleRuntimeEnvironment(it.gradleUserHome, it.gradleVersion) },
        java = javaEnvironment,
        versionInfo = versionInfo
      )
    }

    /**
     * Convert GradleBuild to GradleBuildMetadata.
     */
    private fun GradleBuild.toMetadata(): GradleBuildMetadata {
      return GradleBuildMetadata(
        buildIdentifier = buildIdentifier?.rootDir,
        rootProject = rootProject?.path,
        projects = projects.map { it.path },
        includedBuilds = includedBuilds.map { it.buildIdentifier.rootDir.absolutePath },
        editableBuilds = editableBuilds.map { it.buildIdentifier.rootDir.absolutePath }
      )
    }
  }
}

/**
 * Extension interface for non-Android Gradle projects.
 */
interface IGradleProjectExtension {
  /**
   * Get the project category.
   */
  fun getProjectCategory(): ProjectTypeDetector.ProjectCategory

  /**
   * Get the framework version (e.g., Spring Boot version, Kotlin version).
   */
  fun getFrameworkVersion(): String?

  /**
   * Get additional metadata specific to the project type.
   */
  fun getAdditionalMetadata(): Map<String, String?>
}

/**
 * Extension for Spring Boot projects.
 */
interface ISpringBootProjectExtension : IGradleProjectExtension {
  fun getSpringBootVersion(): String?
  fun getMainClass(): String?
}

/**
 * Extension for Kotlin JVM projects.
 */
interface IKotlinJvmProjectExtension : IGradleProjectExtension {
  fun getKotlinVersion(): String?
  fun getJvmTarget(): String?
}

/**
 * Extension for Java projects.
 */
interface IJavaProjectExtension : IGradleProjectExtension {
  fun getJavaVersion(): String?
}
