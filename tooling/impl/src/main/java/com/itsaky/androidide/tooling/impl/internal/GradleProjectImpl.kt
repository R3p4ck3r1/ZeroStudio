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
import java.io.Serializable
import java.util.concurrent.CompletableFuture
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.gradle.GradleBuild

/** @author Akash Yadav */
internal open class GradleProjectImpl(
    protected val gradleProject: GradleProject,
    private val buildEnvironment: GradleBuildEnvironment? = null,
    private val gradleBuild: GradleBuildMetadata? = null,
) :
    IGradleProject, Serializable {

  private val serialVersionUID = 1L

  override fun getMetadata(): CompletableFuture<ProjectMetadata> {
    return CompletableFuture.supplyAsync {
      ProjectMetadata(
          gradleProject.name,
          gradleProject.path,
          gradleProject.projectDirectory,
          gradleProject.buildDirectory,
          gradleProject.description,
          gradleProject.buildScript.sourceFile,
          ProjectType.Gradle,
      )
    }
  }

  override fun getTasks(): CompletableFuture<List<GradleTask>> {
    return CompletableFuture.supplyAsync {
      mutableListOf<GradleTask>().apply {
        for (task in gradleProject.tasks) {
          add(
              GradleTask(
                  task.name,
                  task.description,
                  task.group,
                  task.path,
                  task.displayName,
                  task.isPublic,
                  task.project.path,
              )
          )
        }
      }
    }
  }

  override fun getBuildEnvironment(): CompletableFuture<GradleBuildEnvironment?> {
    return CompletableFuture.completedFuture(buildEnvironment)
  }

  override fun getGradleBuild(): CompletableFuture<GradleBuildMetadata?> {
    return CompletableFuture.completedFuture(gradleBuild)
  }

  companion object {
    fun from(gradleProject: GradleProject, buildEnvironment: BuildEnvironment?, gradleBuild: GradleBuild?):
        GradleProjectImpl {
      return GradleProjectImpl(
          gradleProject,
          buildEnvironment?.toMetadata(),
          gradleBuild?.toMetadata(),
      )
    }

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
          buildIdentifier?.rootDir,
          gradle?.let { GradleRuntimeEnvironment(it.gradleUserHome, it.gradleVersion) },
          javaEnvironment,
          versionInfo,
      )
    }

    private fun GradleBuild.toMetadata(): GradleBuildMetadata {
      return GradleBuildMetadata(
          buildIdentifier?.rootDir,
          rootProject?.path,
          projects.map { it.path },
          includedBuilds.map { it.buildIdentifier.rootDir.absolutePath },
          editableBuilds.map { it.buildIdentifier.rootDir.absolutePath },
      )
    }
  }
}
