package com.itsaky.androidide.tooling.api.models

import java.io.File
import java.io.Serializable

enum class AndroidModuleType : Serializable {
  APPLICATION,
  LIBRARY,
  TEST,
  DYNAMIC_FEATURE,
  UNKNOWN,
}

data class FlavorMatrixModel(
    val name: String,
    val dimension: String?,
    val minSdkVersion: Int?,
    val targetSdkVersion: Int?,
    val resourceConfigurations: List<String>,
) : Serializable

data class BuildTypeMatrixModel(
    val name: String,
    val isDebuggable: Boolean,
    val isMinifyEnabled: Boolean,
    val signingConfig: String?,
) : Serializable

data class VariantMatrixModel(
    val name: String,
    val buildType: String?,
    val productFlavors: List<String>,
    val deviceTestArtifacts: List<String>,
    val hostTestArtifacts: List<String>,
    val testSuiteArtifacts: List<String>,
    val hasTestFixturesArtifact: Boolean,
) : Serializable

data class SourceSpaceModel(
    val javaDirectories: List<File>,
    val kotlinDirectories: List<File>,
    val manifestFiles: List<File>,
    val resourceDirectories: List<File>,
    val assetsDirectories: List<File>,
    val aidlDirectories: List<File>,
    val resourcesDirectories: List<File>,
    val renderscriptDirectories: List<File>,
    val baselineProfileDirectories: List<File>,
    val keepRulesDirectories: List<File>,
    val aarKeepRulesDirectories: List<File>,
    val shadersDirectories: List<File>,
    val mlModelsDirectories: List<File>,
    val jniLibsDirectories: List<File>,
    val customDirectories: List<File>,
    val generatedSources: GeneratedSourceModel,
) : Serializable

data class GeneratedSourceModel(
    val annotationProcessorSources: List<File>,
    val buildConfigSources: List<File>,
    val viewBindingSources: List<File>,
    val dataBindingSources: List<File>,
) : Serializable

data class DependencyGraphModel(
    val artifactDependencies: List<String>,
    val localJarDependencies: List<File>,
    val aarExplodedFolders: List<File>,
    val aarClassesJars: List<File>,
    val projectDependencies: List<String>,
    val libraries: List<LibraryGraphEntry>,
) : Serializable
