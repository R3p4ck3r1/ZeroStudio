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
package com.itsaky.androidide.tooling.impl.sync

import com.itsaky.androidide.tooling.api.IGradleProject
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.impl.ProjectTypeDetector
import com.itsaky.androidide.tooling.impl.internal.GradleProjectImpl
import com.itsaky.androidide.tooling.impl.internal.NonAndroidProjectImpl
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.GradleBuild
import org.slf4j.LoggerFactory

/**
 * Builds model for non-Android Gradle projects.
 *
 * This includes:
 * - Spring Boot projects
 * - Kotlin JVM projects (applications and libraries)
 * - Java projects (applications and libraries)
 * - Gradle plugin projects
 * - Generic Gradle projects
 *
 * @author Akash Yadav
 */
class NonAndroidProjectBuilder(
    private val initializationParams: InitializeProjectParams
) : AbstractModelBuilder<NonAndroidProjectBuilderParams, IGradleProject>(initializationParams) {

  private val log = LoggerFactory.getLogger(NonAndroidProjectBuilder::class.java)

  override fun build(param: NonAndroidProjectBuilderParams): IGradleProject {
    val (gradleProject, buildEnvironment, gradleBuild) = param

    val detectionResult = ProjectTypeDetector.detect(gradleProject)
    log("Detected project type: {} ({})", detectionResult.category, detectionResult.projectType)

    return NonAndroidProjectImpl.from(
      gradleProject = gradleProject,
      buildEnvironment = buildEnvironment,
      gradleBuild = gradleBuild,
      projectCategory = detectionResult.category,
      detectionResult = detectionResult
    )
  }

  /**
   * Build a model for a generic Gradle project (non-specific type).
   */
  fun buildGeneric(
      gradleProject: GradleProject,
      buildEnvironment: BuildEnvironment?,
      gradleBuild: GradleBuild?
  ): IGradleProject {
    return GradleProjectImpl.from(gradleProject, buildEnvironment, gradleBuild)
  }

  /**
   * Build a model for a Spring Boot project.
   */
  fun buildSpringBoot(
      gradleProject: GradleProject,
      buildEnvironment: BuildEnvironment?,
      gradleBuild: GradleBuild?,
      springBootVersion: String?,
      mainClass: String?
  ): IGradleProject {
    log("Building Spring Boot project model (version: {})", springBootVersion)
    return NonAndroidProjectImpl.createSpringBoot(
      gradleProject = gradleProject,
      buildEnvironment = buildEnvironment,
      gradleBuild = gradleBuild,
      springBootVersion = springBootVersion,
      mainClass = mainClass
    )
  }

  /**
   * Build a model for a Kotlin JVM project.
   */
  fun buildKotlinJvm(
      gradleProject: GradleProject,
      buildEnvironment: BuildEnvironment?,
      gradleBuild: GradleBuild?,
      kotlinVersion: String?,
      jvmTarget: String?
  ): IGradleProject {
    log("Building Kotlin JVM project model (version: {}, target: {})", kotlinVersion, jvmTarget)
    return NonAndroidProjectImpl.createKotlinJvm(
      gradleProject = gradleProject,
      buildEnvironment = buildEnvironment,
      gradleBuild = gradleBuild,
      kotlinVersion = kotlinVersion,
      jvmTarget = jvmTarget
    )
  }

  /**
   * Build a model for a Java project.
   */
  fun buildJava(
      gradleProject: GradleProject,
      buildEnvironment: BuildEnvironment?,
      gradleBuild: GradleBuild?,
      javaVersion: String?
  ): IGradleProject {
    log("Building Java project model (version: {})", javaVersion)
    return NonAndroidProjectImpl.createJava(
      gradleProject = gradleProject,
      buildEnvironment = buildEnvironment,
      gradleBuild = gradleBuild,
      javaVersion = javaVersion
    )
  }
}

/**
 * Parameters for building a non-Android project model.
 */
data class NonAndroidProjectBuilderParams(
    val gradleProject: GradleProject,
    val buildEnvironment: BuildEnvironment?,
    val gradleBuild: GradleBuild?
)

/**
 * Builds model for Spring Boot projects.
 */
class SpringBootProjectBuilder(
    private val initializationParams: InitializeProjectParams
) : AbstractModelBuilder<SpringBootProjectBuilderParams, IGradleProject>(initializationParams) {

  private val log = LoggerFactory.getLogger(SpringBootProjectBuilder::class.java)

  override fun build(param: SpringBootProjectBuilderParams): IGradleProject {
    val (gradleProject, buildEnvironment, gradleBuild, springBootVersion, mainClass) = param

    log("Building Spring Boot project model (version: {})", springBootVersion)

    return NonAndroidProjectImpl.createSpringBoot(
      gradleProject = gradleProject,
      buildEnvironment = buildEnvironment,
      gradleBuild = gradleBuild,
      springBootVersion = springBootVersion,
      mainClass = mainClass
    )
  }
}

/**
 * Parameters for building a Spring Boot project model.
 */
data class SpringBootProjectBuilderParams(
    val gradleProject: GradleProject,
    val buildEnvironment: BuildEnvironment?,
    val gradleBuild: GradleBuild?,
    val springBootVersion: String?,
    val mainClass: String?
)

/**
 * Builds model for Kotlin JVM projects.
 */
class KotlinJvmProjectBuilder(
    private val initializationParams: InitializeProjectParams
) : AbstractModelBuilder<KotlinJvmProjectBuilderParams, IGradleProject>(initializationParams) {

  private val log = LoggerFactory.getLogger(KotlinJvmProjectBuilder::class.java)

  override fun build(param: KotlinJvmProjectBuilderParams): IGradleProject {
    val (gradleProject, buildEnvironment, gradleBuild, kotlinVersion, jvmTarget) = param

    log("Building Kotlin JVM project model (version: {}, target: {})", kotlinVersion, jvmTarget)

    return NonAndroidProjectImpl.createKotlinJvm(
      gradleProject = gradleProject,
      buildEnvironment = buildEnvironment,
      gradleBuild = gradleBuild,
      kotlinVersion = kotlinVersion,
      jvmTarget = jvmTarget
    )
  }
}

/**
 * Parameters for building a Kotlin JVM project model.
 */
data class KotlinJvmProjectBuilderParams(
    val gradleProject: GradleProject,
    val buildEnvironment: BuildEnvironment?,
    val gradleBuild: GradleBuild?,
    val kotlinVersion: String?,
    val jvmTarget: String?
)

/**
 * Builds model for Java projects.
 */
class JavaProjectBuilder(
    private val initializationParams: InitializeProjectParams
) : AbstractModelBuilder<JavaProjectBuilderParams, IGradleProject>(initializationParams) {

  private val log = LoggerFactory.getLogger(JavaProjectBuilder::class.java)

  override fun build(param: JavaProjectBuilderParams): IGradleProject {
    val (gradleProject, buildEnvironment, gradleBuild, javaVersion) = param

    log("Building Java project model (version: {})", javaVersion)

    return NonAndroidProjectImpl.createJava(
      gradleProject = gradleProject,
      buildEnvironment = buildEnvironment,
      gradleBuild = gradleBuild,
      javaVersion = javaVersion
    )
  }
}

/**
 * Parameters for building a Java project model.
 */
data class JavaProjectBuilderParams(
    val gradleProject: GradleProject,
    val buildEnvironment: BuildEnvironment?,
    val gradleBuild: GradleBuild?,
    val javaVersion: String?
)
