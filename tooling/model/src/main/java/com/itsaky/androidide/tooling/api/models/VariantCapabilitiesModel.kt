package com.itsaky.androidide.tooling.api.models

import java.io.File
import java.io.Serializable

data class TestArtifactModel(
    val name: String,
    val assembleTaskName: String?,
    val compileTaskName: String?,
) : Serializable

data class VariantCapabilitiesModel(
    val isInstantAppCompatible: Boolean,
    val runTestInSeparateProcess: Boolean,
    val desugaredMethods: List<File>,
    val experimentalProperties: Map<String, String>,
    val deviceTestArtifacts: List<TestArtifactModel>,
    val hostTestArtifacts: List<TestArtifactModel>,
    val testSuiteArtifacts: List<TestArtifactModel>,
) : Serializable
