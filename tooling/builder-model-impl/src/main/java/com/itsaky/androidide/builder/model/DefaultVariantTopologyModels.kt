package com.itsaky.androidide.builder.model

import java.io.File
import java.io.Serializable

data class DefaultFlavorMatrix(
    val name: String,
    val dimension: String?,
    val minSdkVersion: Int?,
    val targetSdkVersion: Int?,
    val resourceConfigurations: List<String>,
) : Serializable

data class DefaultBuildTypeMatrix(
    val name: String,
    val isDebuggable: Boolean,
    val isMinifyEnabled: Boolean,
    val signingConfig: String?,
) : Serializable

data class DefaultVariantMatrix(
    val name: String,
    val buildType: String?,
    val productFlavors: List<String>,
    val deviceTestArtifacts: List<String>,
    val hostTestArtifacts: List<String>,
    val testSuiteArtifacts: List<String>,
    val hasTestFixturesArtifact: Boolean,
) : Serializable

data class DefaultSourceSpace(
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
    val generatedSources: DefaultGeneratedSources,
) : Serializable

data class DefaultVariantContext(
    val variantName: String,
    val classpath: List<File>,
    val generatedSources: DefaultGeneratedSources,
    val clearOnSwitchGeneratedSources: List<File>,
    val clearOnSwitchSourceDirectories: List<File>,
) : Serializable

data class DefaultGeneratedSources(
    val annotationProcessorSources: List<File>,
    val buildConfigSources: List<File>,
    val viewBindingSources: List<File>,
    val dataBindingSources: List<File>,
) : Serializable
