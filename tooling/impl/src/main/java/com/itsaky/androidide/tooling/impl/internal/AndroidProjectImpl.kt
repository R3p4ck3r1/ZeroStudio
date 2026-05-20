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

import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.GraphItem
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.itsaky.androidide.builder.model.DefaultJavaCompileOptions
import com.itsaky.androidide.builder.model.DefaultLibrary
import com.itsaky.androidide.builder.model.DefaultSourceSetContainer
import com.itsaky.androidide.builder.model.DefaultViewBindingOptions
import com.itsaky.androidide.tooling.api.IAndroidProject
import com.itsaky.androidide.tooling.api.models.AndroidArtifactMetadata
import com.itsaky.androidide.tooling.api.models.AndroidModuleType
import com.itsaky.androidide.tooling.api.models.AndroidProjectMetadata
import com.itsaky.androidide.tooling.api.models.AndroidProjectModelSnapshot
import com.itsaky.androidide.tooling.api.models.AndroidVariantMetadata
import com.itsaky.androidide.tooling.api.models.BasicAndroidVariantMetadata
import com.itsaky.androidide.tooling.api.models.BuildTypeMatrixModel
import com.itsaky.androidide.tooling.api.models.DependencyGraphModel
import com.itsaky.androidide.tooling.api.models.FlavorMatrixModel
import com.itsaky.androidide.tooling.api.models.GeneratedSourceModel
import com.itsaky.androidide.tooling.api.models.ManifestMergerReport
import com.itsaky.androidide.tooling.api.models.MergedPermissionSource
import com.itsaky.androidide.tooling.api.models.ProjectMetadata
import com.itsaky.androidide.tooling.api.models.SourceSpaceModel
import com.itsaky.androidide.tooling.api.models.params.StringParameter
import com.itsaky.androidide.tooling.api.util.AndroidModulePropertyCopier
import com.itsaky.androidide.tooling.api.util.AndroidModulePropertyCopier.copy
import com.itsaky.androidide.utils.AndroidPluginVersion
import com.itsaky.androidide.utils.capitalizeString
import java.io.File
import java.io.Serializable
import java.util.regex.Pattern
import java.util.concurrent.CompletableFuture
import org.gradle.tooling.model.GradleProject

/** @author Akash Yadav */
internal class AndroidProjectImpl(
    gradleProject: GradleProject,
    private val configuredVariant: String,
    private val basicAndroidProject: BasicAndroidProject,
    private val androidProject: AndroidProject,
    private val variantDependencies: VariantDependencies,
    private val versions: Versions,
    private val androidDsl: AndroidDsl,
) : GradleProjectImpl(gradleProject), IAndroidProject, Serializable {

  private val serialVersionUID = 1L

  override fun getConfiguredVariant(): CompletableFuture<String> {
    return CompletableFuture.completedFuture(this.configuredVariant)
  }

  override fun getVariants(): CompletableFuture<List<BasicAndroidVariantMetadata>> {
    return CompletableFuture.supplyAsync {
      androidProject.variants.map {
        BasicAndroidVariantMetadata(it.name, it.mainArtifact.toMetadata(it.name))
      }
    }
  }

  private fun AndroidArtifact.toMetadata(variantName: String): AndroidArtifactMetadata {
    val sourceSpace = computeSourceSpace(variantName)
    return AndroidArtifactMetadata(
        name = variantName,
        applicationId = computeApplicationId(variantName),
        resGenTaskName = resGenTaskName ?: "",
        assembleTaskOutputListingFile = assembleTaskOutputListingFile,
        generatedResourceFolders = generatedResourceFolders,
        generatedSourceFolders = generatedSourceFolders,
        maxSdkVersion = maxSdkVersion,
        minSdkVersion = minSdkVersion.apiLevel,
        signingConfigName = signingConfigName ?: "",
        sourceGenTaskName = sourceGenTaskName ?: "",
        assembleTaskName = assembleTaskName ?: "", // Line 88 - ADD ?: ""
        classJars = classesFolders.filter { it.name.endsWith(".jar") },
        compileTaskName = compileTaskName ?: "", // Line 90 - ADD ?: ""
        targetSdkVersionOverride = targetSdkVersionOverride?.apiLevel ?: -1,
        sourceSpace = sourceSpace,
        dependencyGraph = computeDependencyGraph(),
        manifestMergerReport = parseManifestMergerReport(variantName),
    )
  }

  override fun getVariant(param: StringParameter): CompletableFuture<AndroidVariantMetadata?> {
    return CompletableFuture.supplyAsync {
      androidProject.variants.find { it.name == param.value }?.toMetadata()
    }
  }

  private fun Variant.toMetadata(): AndroidVariantMetadata {
    val moduleType = mapModuleType(basicAndroidProject.projectType)
    val buildTypeModel =
        androidDsl.buildTypes.find { it.name == buildType }?.let {
          BuildTypeMatrixModel(
              name = it.name,
              isDebuggable = it.isDebuggable,
              isMinifyEnabled = it.isMinifyEnabled,
              signingConfig = it.signingConfig,
          )
        }

    return AndroidVariantMetadata(
        name = name,
        mainArtifact = mainArtifact.toMetadata(name),
        otherArtifacts = mutableMapOf(),
        moduleType = moduleType,
        productFlavors =
            productFlavors.map { flavorName ->
              androidDsl.productFlavors.find { it.name == flavorName }?.let {
                FlavorMatrixModel(
                    name = it.name,
                    dimension = it.dimension,
                    minSdkVersion = it.minSdkVersion?.apiLevel,
                    targetSdkVersion = it.targetSdkVersion?.apiLevel,
                    resourceConfigurations = it.resourceConfigurations,
                )
              }
            }.filterNotNull(),
        buildType = buildTypeModel,
    )
  }

  private fun computeSourceSpace(variantName: String): SourceSpaceModel? {
    val sourceSet = basicAndroidProject.sourceSets.firstOrNull { it.name == variantName }
    val generatedBase = File(gradleProject.buildDirectory, "generated")
    return sourceSet?.let {
      SourceSpaceModel(
          javaDirectories = it.javaDirectories,
          kotlinDirectories = it.kotlinDirectories,
          manifestFiles = listOfNotNull(it.manifestFile),
          resourceDirectories = it.resDirectories,
          assetsDirectories = it.assetsDirectories,
          aidlDirectories = it.aidlDirectories,
          jniLibsDirectories = it.jniLibsDirectories,
          customDirectories = it.customDirectories.map { custom -> custom.directory },
          generatedSources =
              GeneratedSourceModel(
                  annotationProcessorSources =
                      (listOf(File(generatedBase, "ap_generated_sources")) + generatedSourceFolders)
                          .distinct()
                          .filter(File::exists),
                  buildConfigSources =
                      listOf(File(generatedBase, "source/buildConfig")).filter(File::exists),
                  viewBindingSources =
                      listOf(File(generatedBase, "view_binding_base_class_source_out")).filter(
                          File::exists
                      ),
                  dataBindingSources =
                      listOf(File(generatedBase, "data_binding_base_class_source_out")).filter(
                          File::exists
                      ),
              ),
      )
    }
  }

  private fun computeDependencyGraph(): DependencyGraphModel {
    val libraries = variantDependencies.libraries.values
    return DependencyGraphModel(
        artifactDependencies =
            libraries.mapNotNull {
              val artifactAddress = it.artifactAddress ?: return@mapNotNull null
              "${artifactAddress.group}:${artifactAddress.name}:${artifactAddress.version}"
            },
        localJarDependencies = libraries.mapNotNull { it.localJar },
        aarExplodedFolders = libraries.mapNotNull { lib -> lib.folder.takeIf { lib.type == LibraryType.ANDROID_LIBRARY } },
        projectDependencies =
            libraries.mapNotNull { lib ->
              lib.projectInfo?.let { "${it.buildId}:${it.projectPath}" }
            },
    )
  }

  private fun parseManifestMergerReport(variantName: String): ManifestMergerReport? {
    val report =
        File(
            gradleProject.buildDirectory,
            "outputs/logs/manifest-merger-$variantName-report.txt",
        )
    if (!report.exists()) return null
    val text = report.readText()
    val permissionPattern =
        Pattern.compile("uses-permission#([a-zA-Z0-9_.]+).*?ADDED from (.+?)(?:\\n|$)")
    val matcher = permissionPattern.matcher(text)
    val merged = mutableListOf<MergedPermissionSource>()
    while (matcher.find()) {
      merged.add(
          MergedPermissionSource(
              permission = matcher.group(1),
              source = matcher.group(2).trim(),
          )
      )
    }
    return ManifestMergerReport(report, merged)
  }

  override fun getBootClasspaths(): CompletableFuture<Collection<File>> {
    return CompletableFuture.supplyAsync { basicAndroidProject.bootClasspath }
  }

  override fun getLibraryMap(): CompletableFuture<Map<String, DefaultLibrary>> {
    return CompletableFuture.supplyAsync {
      val seen = HashMap<String, DefaultLibrary>()
      val compileDependencies = variantDependencies.mainArtifact.compileDependencies
      val libraries = variantDependencies.libraries
      for (dependency in compileDependencies) {
        seen[dependency.key] ?: fillLibrary(dependency, libraries, seen)
      }
      seen
    }
  }

  private fun fillLibrary(
      item: GraphItem,
      libraries: Map<String, Library>,
      seen: HashMap<String, DefaultLibrary>,
  ): DefaultLibrary? {

    val lib = libraries[item.key] ?: return null
    val library = copy(lib)

    for (dependency in item.dependencies) {
      val dep = fillLibrary(dependency, libraries, seen)!!
      library.dependencies.add(dep.key)
    }

    seen[item.key] = library

    return library
  }

  override fun getMainSourceSet(): CompletableFuture<DefaultSourceSetContainer?> {
    return CompletableFuture.supplyAsync {
      basicAndroidProject.mainSourceSet?.let(AndroidModulePropertyCopier::copy)
    }
  }

  override fun getLintCheckJars(): CompletableFuture<List<File>> {
    return CompletableFuture.supplyAsync { androidProject.lintChecksJars }
  }

  private fun getClassesJar(): File {
    // TODO(itsaky): this should handle product flavors as well
    return File(
        gradleProject.buildDirectory,
        "${IAndroidProject.FD_INTERMEDIATES}/compile_library_classes_jar/$configuredVariant/classes.jar",
    )
  }

  override fun getClasspaths(): CompletableFuture<List<File>> {
    return CompletableFuture.supplyAsync {
      mutableListOf<File>().apply {
        add(getClassesJar())
        getVariant(StringParameter(configuredVariant))
            .get()
            ?.mainArtifact
            ?.classJars
            ?.let(this::addAll)
      }
    }
  }

  override fun getMetadata(): CompletableFuture<ProjectMetadata> {
    return CompletableFuture.supplyAsync {
      val gradleMetadata = super.getMetadata().get()

      val viewBindingOptions =
          androidProject.viewBindingOptions?.let(AndroidModulePropertyCopier::copy)
              ?: DefaultViewBindingOptions()

      return@supplyAsync AndroidProjectMetadata(
          gradleMetadata,
          basicAndroidProject.projectType,
          copy(androidProject.flags),
          androidProject.javaCompileOptions?.let { copy(it) }
              ?: DefaultJavaCompileOptions(), // Line 174 - ADD ?: DefaultJavaCompileOptions()
          viewBindingOptions,
          androidProject.resourcePrefix,
          androidProject.namespace,
          androidProject.androidTestNamespace,
          androidProject.testFixturesNamespace,
          computeProjectSnapshot(),
          getClassesJar(),
      )
    }
  }

  private fun computeProjectSnapshot(): AndroidProjectModelSnapshot {
    val moduleType = mapModuleType(basicAndroidProject.projectType)

    val buildTypes =
        androidDsl.buildTypes.map {
          BuildTypeMatrixModel(
              name = it.name,
              isDebuggable = it.isDebuggable,
              isMinifyEnabled = it.isMinifyEnabled,
              signingConfig = it.signingConfig,
          )
        }

    val flavors =
        androidDsl.productFlavors.map {
          FlavorMatrixModel(
              name = it.name,
              dimension = it.dimension,
              minSdkVersion = it.minSdkVersion?.apiLevel,
              targetSdkVersion = it.targetSdkVersion?.apiLevel,
              resourceConfigurations = it.resourceConfigurations,
          )
        }

    return AndroidProjectModelSnapshot(
        moduleType = moduleType,
        buildTypes = buildTypes,
        productFlavors = flavors,
        availableVariants = basicAndroidProject.variants.map { it.name },
    )
  }

  private fun mapModuleType(type: ProjectType): AndroidModuleType =
      when (type) {
        ProjectType.APPLICATION -> AndroidModuleType.APPLICATION
        ProjectType.LIBRARY -> AndroidModuleType.LIBRARY
        ProjectType.TEST -> AndroidModuleType.TEST
        ProjectType.DYNAMIC_FEATURE -> AndroidModuleType.DYNAMIC_FEATURE
        else -> AndroidModuleType.UNKNOWN
      }

  private fun AndroidArtifact.computeApplicationId(variantName: String): String? {
    val minAgpForAppId = AndroidPluginVersion(7, 4, 0)
    return if (minAgpForAppId <= AndroidPluginVersion.parse(versions.agp)) {
      applicationId
    } else {
      computeApplicationIdLegacy(variantName)
    }
  }

  // Adapted from the following :
  // https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:build-system/gradle-core/src/main/java/com/android/build/gradle/internal/core/dsl/impl/ComponentDslInfoImpl.kt;drc=6a5551bdea55c0c991f1ccf1e3f8f6f3d2cd2cb7;l=107
  // https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:build-system/gradle-core/src/main/java/com/android/build/gradle/internal/core/dsl/impl/VariantDslInfoImpl.kt;drc=d44f5b98cd5530eceb230e0d151ad96c4277f78d;l=109

  protected fun computeApplicationIdLegacy(variantName: String): String {
    val basicVariant = basicAndroidProject.variants.firstOrNull { it.name == variantName }
    val buildType =
        basicVariant?.buildType?.let { buildTypeName ->
          androidDsl.buildTypes.find { buildType -> buildType.name == buildTypeName }
        }!!

    val appIdFromFlavor =
        if (basicAndroidProject.projectType == ProjectType.APPLICATION) {
          androidDsl.productFlavors
              .find { flavor ->
                "${flavor.name}${buildType.name.capitalizeString()}" == variantName
              }
              ?.applicationId
        } else {
          androidDsl.defaultConfig.applicationId
        }

    return if (appIdFromFlavor == null) {
      // No appId value set from DSL; use the namespace value from the DSL.
      "${androidProject.namespace}${computeApplicationIdSuffix(variantName, buildType)}"
    } else {
      // use value from flavors/defaultConfig
      // needed to make nullability work in kotlinc
      val finalAppIdFromFlavors: String = appIdFromFlavor
      "$finalAppIdFromFlavors${computeApplicationIdSuffix(variantName, buildType)}"
    }
  }

  /**
   * Combines all the appId suffixes into a single one.
   *
   * The suffixes are separated by '.' whether their first char is a '.' or not.
   */
  protected fun computeApplicationIdSuffix(variantName: String, buildType: BuildType): String {
    // for the suffix we combine the suffix from all the flavors. However, we're going to
    // want the higher priority one to be last.
    val suffixes = mutableListOf<String>()
    androidDsl.defaultConfig.applicationIdSuffix?.let { suffixes.add(it) }

    if (basicAndroidProject.projectType == ProjectType.APPLICATION) {

      val flavorSuffix =
          androidDsl.productFlavors
              .find { flavor ->
                "${flavor.name}${buildType.name.capitalizeString()}" == variantName
              }
              ?.applicationIdSuffix

      flavorSuffix?.also { suffixes.add(flavorSuffix) }

      // then we add the build type after.
      buildType.applicationIdSuffix?.also { suffixes.add(it) }
    }

    val nonEmptySuffixes = suffixes.filter { it.isNotEmpty() }
    return if (nonEmptySuffixes.isNotEmpty()) {
      ".${nonEmptySuffixes.joinToString(separator = ".", transform = { it.removePrefix(".") })}"
    } else {
      ""
    }
  }
}
