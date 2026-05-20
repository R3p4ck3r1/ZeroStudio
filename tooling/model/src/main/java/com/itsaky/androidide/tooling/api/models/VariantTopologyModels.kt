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

data class SourceSpaceModel(
    val javaDirectories: List<File>,
    val kotlinDirectories: List<File>,
    val manifestFiles: List<File>,
    val resourceDirectories: List<File>,
    val assetsDirectories: List<File>,
    val aidlDirectories: List<File>,
    val jniLibsDirectories: List<File>,
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
) : Serializable
