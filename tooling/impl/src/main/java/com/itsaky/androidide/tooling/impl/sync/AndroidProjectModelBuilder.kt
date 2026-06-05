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

import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.ModelBuilderParameter
import com.android.builder.model.v2.models.ProjectSyncIssues
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.itsaky.androidide.builder.model.agp.AgpModelAdapterFactory
import com.itsaky.androidide.builder.model.agp.AgpVersion
import com.itsaky.androidide.builder.model.agp.AgpVersionChecker
import com.itsaky.androidide.builder.model.agp.IAgpModelAdapter
import com.itsaky.androidide.tooling.api.IAndroidProject
import com.itsaky.androidide.tooling.api.messages.InitializeProjectParams
import com.itsaky.androidide.tooling.impl.internal.AndroidProjectImpl
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.BuildController
import org.slf4j.LoggerFactory

/**
 * Builds model for Android application and library projects.
 *
 * Supports AGP 7.x, 8.x, and 9.x through version-specific adapters.
 *
 * @author Akash Yadav
 */
class AndroidProjectModelBuilder(initializationParams: InitializeProjectParams) :
    AbstractModelBuilder<AndroidProjectModelBuilderParams, IAndroidProject>(initializationParams) {

  private val log = LoggerFactory.getLogger(AndroidProjectModelBuilder::class.java)

  override fun build(param: AndroidProjectModelBuilderParams): IAndroidProject {
    val (controller, module, versions, syncIssueReporter) = param

    val androidParams = initializationParams.androidParams
    val projectPath = module.gradleProject.path

    // Detect AGP version and create appropriate adapter
    val agpVersion = detectAgpVersion(module.gradleProject)
    val adapter = createAdapter(agpVersion)

    log("Detected AGP version: {}, using adapter: {}", agpVersion, adapter.javaClass.simpleName)

    val basicModel = controller.getModelAndLog(module, BasicAndroidProject::class.java)
    val androidModel = controller.getModelAndLog(module, AndroidProject::class.java)
    val androidDsl = controller.getModelAndLog(module, AndroidDsl::class.java)

    // AGP 9.x introduces Versions interface - try to fetch it
    val modelVersions = fetchVersionsIfAvailable(controller, module, agpVersion)

    val variantNames = basicModel.variants.map { it.name }
    log("${variantNames.size} build variants found for project '$projectPath': $variantNames")

    var androidVariant = androidParams.variantSelections[projectPath]

    if (androidVariant != null && !variantNames.contains(androidVariant)) {
      log(
          "Configured variant '$androidVariant' not found for project '$projectPath'. Falling back to default variant."
      )
      androidVariant = null
    }

    val configurationVariant = androidVariant ?: variantNames.firstOrNull()
    if (configurationVariant.isNullOrBlank()) {
      throw ModelBuilderException(
          "No variant found for project '$projectPath'. providedVariant=$androidVariant"
      )
    }

    log("Selected build variant '$configurationVariant' for project '$projectPath'")

    try {
      log("Forcing dependency resolution for Android module: $projectPath")
      var downloadedCount = 0
      for (dependency in module.dependencies) {
        if (dependency is IdeaSingleEntryLibraryDependency) {
          try {
            val file = dependency.file
            if (file.exists()) {
              downloadedCount++
            }
          } catch (fileEx: Exception) {
            log("Failed to access dependency file: ${fileEx.message}")
          }
        }
      }
      log("Forced resolution of $downloadedCount dependencies for module: $projectPath")
    } catch (resEx: Exception) {
      log("Failed to pre-resolve dependencies: ${resEx.message}")
    }

    val variantDependencies =
        controller.getModelAndLog(
            module,
            VariantDependencies::class.java,
            ModelBuilderParameter::class.java,
        ) {
          it.variantName = configurationVariant
          it.additionalArtifactsInModel = true
          it.dontBuildRuntimeClasspath = false
          it.dontBuildUnitTestRuntimeClasspath = true
          it.dontBuildScreenshotTestRuntimeClasspath = true
          it.dontBuildAndroidTestRuntimeClasspath = true
          it.dontBuildTestFixtureRuntimeClasspath = true
          it.dontBuildHostTestRuntimeClasspath = emptyMap()
        }

    controller.getModel(module, ProjectSyncIssues::class.java)?.also { syncIssues ->
      syncIssueReporter.reportAll(syncIssues)
    }

    return AndroidProjectImpl(
        module.gradleProject,
        configurationVariant,
        basicModel,
        androidModel,
        variantDependencies,
        versions,
        androidDsl,
        agpVersion
    )
  }

  /**
   * Detect the AGP version from the Gradle project.
   */
  private fun detectAgpVersion(gradleProject: org.gradle.tooling.model.GradleProject): AgpVersion {
    // First try from versions parameter (if available from caller)
    val fromParam = initializationParams.androidParams.let {
      if (it != null && it is com.itsaky.androidide.tooling.api.messages.AndroidParams) {
        // Try to get version from params if available
        null
      } else null
    }

    // Use AgpVersionChecker to detect version
    return AgpVersionChecker.detect(gradleProject)
      ?: AgpVersion.parse("8.2.0") // Default fallback version
  }

  /**
   * Create an appropriate model adapter for the detected AGP version.
   */
  private fun createAdapter(version: AgpVersion): IAgpModelAdapter {
    return try {
      AgpModelAdapterFactory.createAdapter(version)
    } catch (e: UnsupportedOperationException) {
      log.warn("Failed to create adapter for version {}: {}", version, e.message)
      // Return a fallback adapter
      AgpModelAdapterFactory.createAdapter("8.2.0")
    }
  }

  /**
   * Fetch Versions model if available (AGP 9.x+).
   */
  private fun fetchVersionsIfAvailable(
    controller: BuildController,
    module: IdeaModule,
    agpVersion: AgpVersion
  ): Versions? {
    // Versions interface only exists in AGP 9.x
    if (agpVersion.major < 9) {
      log("Versions model not available for AGP {}. Skipping.", agpVersion)
      return null
    }

    return try {
      // Use getModel instead of findModel as BuildController only has getModel
      controller.getModel(module, Versions::class.java).also {
        log("Successfully fetched Versions model for AGP {}", agpVersion)
      }
    } catch (e: org.gradle.tooling.UnknownModelException) {
      log("Versions model not available for AGP {}: {}", agpVersion, e.message)
      null
    } catch (e: Exception) {
      log.warn("Failed to fetch Versions model for AGP {}: {}", agpVersion, e.message)
      null
    }
  }
}
