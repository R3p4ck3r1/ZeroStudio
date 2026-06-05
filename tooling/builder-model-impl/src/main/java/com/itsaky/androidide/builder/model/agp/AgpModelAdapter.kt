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

import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.v2.ide.AndroidLibraryData
import com.android.builder.model.v2.ide.ApiVersion
import com.android.builder.model.v2.ide.ArtifactDependencies
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.SourceProvider
import com.android.builder.model.v2.ide.SourceSetContainer
import com.android.builder.model.v2.ide.TestSuiteArtifact
import com.android.builder.model.v2.ide.Variant
import com.android.builder.model.v2.ide.ViewBindingOptions
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.VariantDependencies
import com.android.builder.model.v2.models.Versions
import com.itsaky.androidide.builder.model.DefaultAndroidGradlePluginProjectFlags
import com.itsaky.androidide.builder.model.DefaultApiVersion
import com.itsaky.androidide.builder.model.DefaultLibrary
import com.itsaky.androidide.builder.model.DefaultLibraryInfo
import com.itsaky.androidide.builder.model.DefaultSourceProvider
import com.itsaky.androidide.builder.model.DefaultSourceSetContainer
import com.itsaky.androidide.builder.model.DefaultViewBindingOptions
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.GradleProject
import org.slf4j.LoggerFactory

/**
 * AGP 9.x model adapter that handles the differences between AGP 9.x and earlier versions.
 *
 * AGP 9.x introduces:
 * - [Versions] interface with MINIMUM_MODEL_CONSUMER and MODEL_PRODUCER constants
 * - New namespace fields: androidTestNamespace, testFixturesNamespace
 * - KTS-first approach with mandatory namespace
 *
 * @author Akash Yadav
 */
@Suppress("UNUSED")
class Agp9ModelAdapter(
  override val agpVersion: AgpVersion
) : IAgpModelAdapter {

  private val log = LoggerFactory.getLogger(Agp9ModelAdapter::class.java)

  init {
    require(agpVersion.major >= 9) {
      "Agp9ModelAdapter only supports AGP 9.x, got $agpVersion"
    }
  }

  override val modelVersion: Int = 3

  override fun adaptBasicAndroidProject(
    gradleProject: GradleProject,
    basicAndroidProject: BasicAndroidProject,
    buildEnvironment: BuildEnvironment?
  ): AdaptedBasicAndroidProject {
    log.debug("Adapting BasicAndroidProject for AGP {}", agpVersion)

    return AdaptedBasicAndroidProject(
      path = basicAndroidProject.path,
      name = basicAndroidProject.name,
      projectType = basicAndroidProject.projectType,
      buildFolder = basicAndroidProject.buildFolder,
      bootClasspath = basicAndroidProject.bootClasspath,
      mainSourceSet = basicAndroidProject.mainSourceSet?.let { adaptSourceSetContainer(it) },
      buildTypeSourceSets = basicAndroidProject.buildTypeSourceSets.map { adaptSourceSetContainer(it) },
      productFlavorSourceSets = basicAndroidProject.productFlavorSourceSets.map { adaptSourceSetContainer(it) },
      variants = basicAndroidProject.variants.map { adaptBasicVariant(it) },
      testSuites = emptyList(), // AGP 9.x specific
      // Deprecated fields (for compatibility)
      buildName = "",
      // Additional fields
      isLibrary = basicAndroidProject.projectType == ProjectType.LIBRARY,
      isDynamicFeature = basicAndroidProject.projectType == ProjectType.DYNAMIC_FEATURE
    )
  }

  override fun adaptAndroidProject(
    gradleProject: GradleProject,
    androidProject: AndroidProject,
    buildEnvironment: BuildEnvironment?
  ): AdaptedAndroidProject {
    log.debug("Adapting AndroidProject for AGP {}", agpVersion)

    return AdaptedAndroidProject(
      namespace = androidProject.namespace,
      androidTestNamespace = androidProject.androidTestNamespace,
      testFixturesNamespace = androidProject.testFixturesNamespace,
      resourcePrefix = androidProject.resourcePrefix,
      projectType = androidProject.projectType,
      dynamicFeatures = androidProject.dynamicFeatures ?: emptyList(),
      variants = androidProject.variants.map { adaptVariant(it) },
      testSuites = androidProject.testSuites.map { adaptTestSuite(it) },
      javaCompileOptions = androidProject.javaCompileOptions,
      viewBindingOptions = androidProject.viewBindingOptions?.let { adaptViewBindingOptions(it) },
      flags = adaptFlags(androidProject.flags),
      lintChecksJars = androidProject.lintChecksJars,
      desugarLibConfig = androidProject.desugarLibConfig,
      lintJar = androidProject.lintJar
    )
  }

  override fun adaptAndroidDsl(androidDsl: AndroidDsl): AdaptedAndroidDsl {
    log.debug("Adapting AndroidDsl for AGP {}", agpVersion)

    return AdaptedAndroidDsl(
      namespace = androidDsl.namespace,
      packageName = androidDsl.packageName,
      compileSdk = androidDsl.compileSdk,
      buildToolsVersion = androidDsl.buildToolsVersion,
      defaultConfig = androidDsl.defaultConfig?.let { adaptBaseConfig(it) },
      buildTypes = androidDsl.buildTypes.map { adaptBuildType(it) },
      productFlavors = androidDsl.productFlavors.map { adaptProductFlavor(it) },
      signingConfigs = emptyMap() // AGP 9.x handles this differently
    )
  }

  override fun adaptVariantDependencies(variantDependencies: VariantDependencies): AdaptedVariantDependencies {
    log.debug("Adapting VariantDependencies for AGP {}", agpVersion)

    return AdaptedVariantDependencies(
      variantName = variantDependencies.variantName,
      mainArtifact = variantDependencies.mainArtifact,
      androidTestArtifacts = variantDependencies.androidTestArtifacts,
      testFixtureArtifacts = variantDependencies.testFixtureArtifacts,
      hostTestArtifacts = variantDependencies.hostTestArtifacts,
      libraries = variantDependencies.libraries,
      mainArtifactCompileDependencies = variantDependencies.mainArtifact.compileDependencies,
      mainArtifactRuntimeDependencies = variantDependencies.mainArtifact.runtimeDependencies
    )
  }

  override fun adaptVersions(versions: Versions): AdaptedVersions {
    log.debug("Adapting Versions for AGP {}", agpVersion)

    return AdaptedVersions(
      agpVersion = AgpVersion.parse(versions.agp),
      modelVersions = versions.versions.mapValues { entry ->
        AdaptedVersionInfo(
          major = entry.value.major,
          minor = entry.value.minor,
          humanReadable = entry.value.humanReadable
        )
      },
      minimumModelConsumer = versions.versions[Versions.MINIMUM_MODEL_CONSUMER]?.let {
        AdaptedVersionInfo(it.major, it.minor, it.humanReadable)
      },
      modelProducer = versions.versions[Versions.MODEL_PRODUCER]?.let {
        AdaptedVersionInfo(it.major, it.minor, it.humanReadable)
      }
    )
  }

  // ========== Helper adaptation methods ==========

  private fun adaptSourceSetContainer(container: SourceSetContainer): AdaptedSourceSetContainer {
    return AdaptedSourceSetContainer(
      name = container.name,
      manifest = container.manifest,
      javaDirectories = container.javaDirectories,
      resDirectories = container.resDirectories,
      assetsDirectories = container.assetsDirectories,
      aidlDirectories = container.aidlDirectories,
      renderscriptDirectories = container.renderscriptDirectories,
      nativeLibraries = container.nativeLibraries,
      shaderDirectories = container.shaderDirectories,
      mlModels = container.mlModels
    )
  }

  private fun adaptSourceProvider(provider: SourceProvider): AdaptedSourceProvider {
    return AdaptedSourceProvider(
      name = provider.name,
      manifest = provider.manifest,
      javaDirectories = provider.javaDirectories,
      resDirectories = provider.resDirectories,
      assetsDirectories = provider.assetsDirectories,
      aidlDirectories = provider.aidlDirectories,
      renderscriptDirectories = provider.renderscriptDirectories,
      nativeLibraries = provider.nativeLibraries,
      shaderDirectories = provider.shaderDirectories,
      mlModels = provider.mlModels
    )
  }

  private fun adaptBasicVariant(variant: BasicVariant): AdaptedBasicVariant {
    return AdaptedBasicVariant(
      name = variant.name,
      displayName = variant.displayName,
      buildType = variant.buildType,
      productFlavors = variant.productFlavors,
      sourceSet = variant.sourceSet?.let { adaptSourceSetContainer(it) }
    )
  }

  private fun adaptVariant(variant: Variant): AdaptedVariant {
    return AdaptedVariant(
      name = variant.name,
      displayName = variant.displayName,
      mainArtifact = variant.mainArtifact,
      androidTestArtifact = variant.androidTestArtifact,
      testFixturesArtifact = variant.testFixturesArtifact,
      unitTestArtifact = variant.unitTestArtifact,
      testedTargetVariant = variant.testedTargetVariant,
      runTestInSeparateProcess = variant.runTestInSeparateProcess,
      deviceTestArtifacts = variant.deviceTestArtifacts,
      hostTestArtifacts = variant.hostTestArtifacts,
      testSuiteArtifacts = variant.testSuiteArtifacts,
      desugaredMethods = variant.desugaredMethods,
      isInstantAppCompatible = variant.isInstantAppCompatible,
      experimentalProperties = variant.experimentalProperties
    )
  }

  private fun adaptTestSuite(testSuite: TestSuiteArtifact): AdaptedTestSuite {
    return AdaptedTestSuite(
      name = testSuite.name,
      applicationId = testSuite.applicationId,
      testApplicationId = testSuite.testApplicationId,
      instrumentationPackage = testSuite.instrumentationPackage
    )
  }

  private fun adaptViewBindingOptions(options: ViewBindingOptions): AdaptedViewBindingOptions {
    return AdaptedViewBindingOptions(
      isEnabled = options.isEnabled,
      bindingModes = emptyMap() // AGP 9.x doesn't use this
    )
  }

  private fun adaptFlags(flags: AndroidGradlePluginProjectFlags): AdaptedFlags {
    return AdaptedFlags(
      isJetifier = flags.isJetifier,
      isR8FullMode = flags.isR8FullMode,
      isCoreLibraryDesugaringEnabled = flags.isCoreLibraryDesugaringEnabled,
      useAndroidX = flags.useAndroidX,
      useNonTransitiveRClass = flags.useNonTransitiveRClass,
      useFullClasspathForR8 = flags.useFullClasspathForR8
    )
  }

  private fun adaptBaseConfig(config: Any): AdaptedBaseConfig {
    // AGP 9.x BaseConfig is different, adapt accordingly
    return AdaptedBaseConfig(
      applicationId = getFieldValue(config, "applicationId"),
      minSdkVersion = getFieldValue(config, "minSdkVersion")?.let { adaptApiVersion(it) },
      targetSdkVersion = getFieldValue(config, "targetSdkVersion")?.let { adaptApiVersion(it) },
      versionCode = getFieldValue(config, "versionCode"),
      versionName = getFieldValue(config, "versionName")
    )
  }

  private fun adaptBuildType(bt: Any): AdaptedBuildType {
    return AdaptedBuildType(
      name = getFieldValue(bt, "name") ?: "",
      isDebuggable = getFieldValue(bt, "isDebuggable") ?: false,
      isTestable = getFieldValue(bt, "isTestable") ?: true,
      isMinifyEnabled = getFieldValue(bt, "isMinifyEnabled") ?: false,
      isShrinkResources = getFieldValue(bt, "isShrinkResources") ?: false,
      signingConfigName = getFieldValue(bt, "signingConfigName")
    )
  }

  private fun adaptProductFlavor(pf: Any): AdaptedProductFlavor {
    return AdaptedProductFlavor(
      name = getFieldValue(pf, "name") ?: "",
      dimension = getFieldValue(pf, "dimension"),
      applicationId = getFieldValue(pf, "applicationId"),
      versionName = getFieldValue(pf, "versionName"),
      versionCode = getFieldValue(pf, "versionCode"),
      minSdkVersion = getFieldValue(pf, "minSdkVersion")?.let { adaptApiVersion(it) },
      maxSdkVersion = getFieldValue(pf, "maxSdkVersion"),
      wearAppLink = getFieldValue(pf, "wearAppLink")
    )
  }

  private fun adaptApiVersion(apiVersion: Any): AdaptedApiVersion {
    return AdaptedApiVersion(
      apiLevel = getFieldValue(apiVersion, "apiLevel") ?: 1,
      codename = getFieldValue(apiVersion, "codename"),
      isPreview = getFieldValue(apiVersion, "isPreview") ?: false
    )
  }

  @Suppress("UNCHECKED_CAST", "BanReflectionForSuggestions")
  private fun <T> getFieldValue(obj: Any, fieldName: String): T? {
    return try {
      val field = obj.javaClass.getDeclaredField(fieldName)
      field.isAccessible = true
      field.get(obj) as? T
    } catch (e: Exception) {
      log.trace("Failed to get field {} from {}: {}", fieldName, obj.javaClass.name, e.message)
      null
    }
  }
}

/**
 * Interface for AGP model adapters.
 */
interface IAgpModelAdapter {
  val agpVersion: AgpVersion
  val modelVersion: Int

  fun adaptBasicAndroidProject(
    gradleProject: GradleProject,
    basicAndroidProject: BasicAndroidProject,
    buildEnvironment: BuildEnvironment?
  ): AdaptedBasicAndroidProject

  fun adaptAndroidProject(
    gradleProject: GradleProject,
    androidProject: AndroidProject,
    buildEnvironment: BuildEnvironment?
  ): AdaptedAndroidProject

  fun adaptAndroidDsl(androidDsl: AndroidDsl): AdaptedAndroidDsl

  fun adaptVariantDependencies(variantDependencies: VariantDependencies): AdaptedVariantDependencies

  fun adaptVersions(versions: Versions): AdaptedVersions
}

/**
 * Factory for creating AGP model adapters based on version.
 */
object AgpModelAdapterFactory {

  private val log = LoggerFactory.getLogger(AgpModelAdapterFactory::class.java)

  /**
   * Create an [IAgpModelAdapter] for the given [AgpVersion].
   *
   * @param version The AGP version.
   * @return The appropriate adapter for the version.
   * @throws UnsupportedOperationException if the version is not supported.
   */
  fun createAdapter(version: AgpVersion): IAgpModelAdapter {
    return when {
      version.major == 7 -> {
        log.debug("Creating AGP 7.x adapter")
        Agp7ModelAdapter(version)
      }
      version.major == 8 -> {
        log.debug("Creating AGP 8.x adapter")
        Agp8ModelAdapter(version)
      }
      version.major >= 9 -> {
        log.debug("Creating AGP 9.x adapter")
        Agp9ModelAdapter(version)
      }
      else -> throw UnsupportedOperationException(
        "Unsupported AGP version: $version. Supported versions are 7.x, 8.x, and 9.x"
      )
    }
  }

  /**
   * Create an [IAgpModelAdapter] from a version string.
   *
   * @param versionString The version string (e.g., "9.3.0-alpha06").
   * @return The appropriate adapter for the version.
   */
  fun createAdapter(versionString: String): IAgpModelAdapter {
    return createAdapter(AgpVersion.parse(versionString))
  }

  /**
   * Get the adapter class for the given version.
   */
  fun getAdapterClass(version: AgpVersion): Class<out IAgpModelAdapter> {
    return when {
      version.major == 7 -> Agp7ModelAdapter::class.java
      version.major == 8 -> Agp8ModelAdapter::class.java
      version.major >= 9 -> Agp9ModelAdapter::class.java
      else -> throw UnsupportedOperationException("Unsupported AGP version: $version")
    }
  }
}

/**
 * AGP 7.x model adapter.
 */
class Agp7ModelAdapter(override val agpVersion: AgpVersion) : IAgpModelAdapter {
  init {
    require(agpVersion.major == 7) { "Agp7ModelAdapter only supports AGP 7.x, got $agpVersion" }
  }
  override val modelVersion: Int = 1

  override fun adaptBasicAndroidProject(
    gradleProject: GradleProject,
    basicAndroidProject: BasicAndroidProject,
    buildEnvironment: BuildEnvironment?
  ): AdaptedBasicAndroidProject {
    throw UnsupportedOperationException("AGP 7.x requires legacy model handling")
  }

  override fun adaptAndroidProject(
    gradleProject: GradleProject,
    androidProject: AndroidProject,
    buildEnvironment: BuildEnvironment?
  ): AdaptedAndroidProject {
    throw UnsupportedOperationException("AGP 7.x requires legacy model handling")
  }

  override fun adaptAndroidDsl(androidDsl: AndroidDsl): AdaptedAndroidDsl {
    throw UnsupportedOperationException("AGP 7.x requires legacy model handling")
  }

  override fun adaptVariantDependencies(variantDependencies: VariantDependencies): AdaptedVariantDependencies {
    throw UnsupportedOperationException("AGP 7.x requires legacy model handling")
  }

  override fun adaptVersions(versions: Versions): AdaptedVersions {
    throw UnsupportedOperationException("AGP 7.x does not have Versions interface")
  }
}

/**
 * AGP 8.x model adapter.
 */
class Agp8ModelAdapter(override val agpVersion: AgpVersion) : IAgpModelAdapter {
  init {
    require(agpVersion.major == 8) { "Agp8ModelAdapter only supports AGP 8.x, got $agpVersion" }
  }
  override val modelVersion: Int = 2

  override fun adaptBasicAndroidProject(
    gradleProject: GradleProject,
    basicAndroidProject: BasicAndroidProject,
    buildEnvironment: BuildEnvironment?
  ): AdaptedBasicAndroidProject {
    // AGP 8.0-8.7 uses v2 models similar to 9.x but without some fields
    return Agp9ModelAdapter(agpVersion).adaptBasicAndroidProject(
      gradleProject, basicAndroidProject, buildEnvironment
    )
  }

  override fun adaptAndroidProject(
    gradleProject: GradleProject,
    androidProject: AndroidProject,
    buildEnvironment: BuildEnvironment?
  ): AdaptedAndroidProject {
    // AGP 8.0-8.7 uses v2 models similar to 9.x but without some fields
    return Agp9ModelAdapter(agpVersion).adaptAndroidProject(
      gradleProject, androidProject, buildEnvironment
    )
  }

  override fun adaptAndroidDsl(androidDsl: AndroidDsl): AdaptedAndroidDsl {
    return Agp9ModelAdapter(agpVersion).adaptAndroidDsl(androidDsl)
  }

  override fun adaptVariantDependencies(variantDependencies: VariantDependencies): AdaptedVariantDependencies {
    return Agp9ModelAdapter(agpVersion).adaptVariantDependencies(variantDependencies)
  }

  override fun adaptVersions(versions: Versions): AdaptedVersions {
    // AGP 8.x doesn't have Versions interface, return a placeholder
    return AdaptedVersions(
      agpVersion = agpVersion,
      modelVersions = emptyMap(),
      minimumModelConsumer = null,
      modelProducer = null
    )
  }
}

// ========== Adapted data classes ==========

/** Adapted BasicAndroidProject for internal use. */
data class AdaptedBasicAndroidProject(
  val path: String,
  val name: String,
  val projectType: ProjectType,
  val buildFolder: java.io.File,
  val bootClasspath: Collection<java.io.File>,
  val mainSourceSet: AdaptedSourceSetContainer?,
  val buildTypeSourceSets: Collection<AdaptedSourceSetContainer>,
  val productFlavorSourceSets: Collection<AdaptedSourceSetContainer>,
  val variants: Collection<AdaptedBasicVariant>,
  val testSuites: Collection<AdaptedTestSuite>,
  val buildName: String,
  val isLibrary: Boolean,
  val isDynamicFeature: Boolean
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted SourceSetContainer for internal use. */
data class AdaptedSourceSetContainer(
  val name: String,
  val manifest: java.io.File?,
  val javaDirectories: Collection<java.io.File>,
  val resDirectories: Collection<java.io.File>,
  val assetsDirectories: Collection<java.io.File>,
  val aidlDirectories: Collection<java.io.File>,
  val renderscriptDirectories: Collection<java.io.File>,
  val nativeLibraries: Collection<java.io.File>,
  val shaderDirectories: Collection<java.io.File>,
  val mlModels: Collection<java.io.File>
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted SourceProvider for internal use. */
data class AdaptedSourceProvider(
  val name: String,
  val manifest: java.io.File?,
  val javaDirectories: Collection<java.io.File>,
  val resDirectories: Collection<java.io.File>,
  val assetsDirectories: Collection<java.io.File>,
  val aidlDirectories: Collection<java.io.File>,
  val renderscriptDirectories: Collection<java.io.File>,
  val nativeLibraries: Collection<java.io.File>,
  val shaderDirectories: Collection<java.io.File>,
  val mlModels: Collection<java.io.File>
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted BasicVariant for internal use. */
data class AdaptedBasicVariant(
  val name: String,
  val displayName: String,
  val buildType: String,
  val productFlavors: Collection<String>,
  val sourceSet: AdaptedSourceSetContainer?
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted AndroidProject for internal use. */
data class AdaptedAndroidProject(
  val namespace: String,
  val androidTestNamespace: String?,
  val testFixturesNamespace: String?,
  val resourcePrefix: String?,
  val projectType: ProjectType,
  val dynamicFeatures: Collection<String>,
  val variants: Collection<AdaptedVariant>,
  val testSuites: Collection<AdaptedTestSuite>,
  val javaCompileOptions: Any?, // JavaCompileOptions
  val viewBindingOptions: AdaptedViewBindingOptions?,
  val flags: AdaptedFlags,
  val lintChecksJars: Collection<java.io.File>,
  val desugarLibConfig: Collection<java.io.File>,
  val lintJar: java.io.File?
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted Variant for internal use. */
data class AdaptedVariant(
  val name: String,
  val displayName: String,
  val mainArtifact: Any, // AndroidArtifact
  val androidTestArtifact: Any?, // AndroidArtifact
  val testFixturesArtifact: Any?, // AndroidArtifact
  val unitTestArtifact: Any?, // JavaArtifact
  val testedTargetVariant: Any?, // TestedTargetVariant
  val runTestInSeparateProcess: Boolean,
  val deviceTestArtifacts: Map<String, Any>,
  val hostTestArtifacts: Map<String, Any>,
  val testSuiteArtifacts: Map<String, Any>,
  val desugaredMethods: Collection<java.io.File>,
  val isInstantAppCompatible: Boolean,
  val experimentalProperties: Map<String, String>
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted TestSuite for internal use. */
data class AdaptedTestSuite(
  val name: String,
  val applicationId: String?,
  val testApplicationId: String?,
  val instrumentationPackage: String?
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted ViewBindingOptions for internal use. */
data class AdaptedViewBindingOptions(
  val isEnabled: Boolean,
  val bindingModes: Map<String, Any>
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted Flags for internal use. */
data class AdaptedFlags(
  val isJetifier: Boolean,
  val isR8FullMode: Boolean,
  val isCoreLibraryDesugaringEnabled: Boolean,
  val useAndroidX: Boolean,
  val useNonTransitiveRClass: Boolean,
  val useFullClasspathForR8: Boolean
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted AndroidDsl for internal use. */
data class AdaptedAndroidDsl(
  val namespace: String?,
  val packageName: String?,
  val compileSdk: Int?,
  val buildToolsVersion: String?,
  val defaultConfig: AdaptedBaseConfig?,
  val buildTypes: Collection<AdaptedBuildType>,
  val productFlavors: Collection<AdaptedProductFlavor>,
  val signingConfigs: Map<String, Any>
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted BaseConfig for internal use. */
data class AdaptedBaseConfig(
  val applicationId: String?,
  val minSdkVersion: AdaptedApiVersion?,
  val targetSdkVersion: AdaptedApiVersion?,
  val versionCode: Int?,
  val versionName: String?
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted BuildType for internal use. */
data class AdaptedBuildType(
  val name: String,
  val isDebuggable: Boolean,
  val isTestable: Boolean,
  val isMinifyEnabled: Boolean,
  val isShrinkResources: Boolean,
  val signingConfigName: String?
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted ProductFlavor for internal use. */
data class AdaptedProductFlavor(
  val name: String,
  val dimension: String?,
  val applicationId: String?,
  val versionName: String?,
  val versionCode: Int?,
  val minSdkVersion: AdaptedApiVersion?,
  val maxSdkVersion: Int?,
  val wearAppLink: String?
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted ApiVersion for internal use. */
data class AdaptedApiVersion(
  val apiLevel: Int,
  val codename: String?,
  val isPreview: Boolean
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted VariantDependencies for internal use. */
data class AdaptedVariantDependencies(
  val variantName: String,
  val mainArtifact: Any,
  val androidTestArtifacts: Map<String, Any>,
  val testFixtureArtifacts: Map<String, Any>,
  val hostTestArtifacts: Map<String, Any>,
  val libraries: Map<String, Library>,
  val mainArtifactCompileDependencies: ArtifactDependencies,
  val mainArtifactRuntimeDependencies: ArtifactDependencies
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted Versions for internal use. */
data class AdaptedVersions(
  val agpVersion: AgpVersion,
  val modelVersions: Map<String, AdaptedVersionInfo>,
  val minimumModelConsumer: AdaptedVersionInfo?,
  val modelProducer: AdaptedVersionInfo?
) : java.io.Serializable {
  private val serialVersionUID = 1L
}

/** Adapted VersionInfo for internal use. */
data class AdaptedVersionInfo(
  val major: Int,
  val minor: Int,
  val humanReadable: String?
) : java.io.Serializable {
  private val serialVersionUID = 1L
}
