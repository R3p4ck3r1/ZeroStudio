package com.itsaky.androidide.tooling.api.models

import java.io.File
import java.io.Serializable

data class TestArtifactModel(
    val name: String,
    val assembleTaskName: String?,
    val compileTaskName: String?,
) : Serializable

data class TestSuiteTargetModel(
    val name: String,
    val testTaskName: String,
    val targetedDevices: Collection<String>,
) : Serializable

data class TestSuiteInfoModel(
    val includedEngines: Set<String>,
    val targets: List<TestSuiteTargetModel>,
) : Serializable

data class TestedTargetVariantModel(
    val targetProjectPath: String,
    val targetVariant: String,
) : Serializable

data class VariantCapabilitiesModel(
    val isInstantAppCompatible: Boolean,
    val runTestInSeparateProcess: Boolean,
    val desugaredMethods: List<File>,
    val experimentalProperties: Map<String, String>,
    val deviceTestArtifacts: List<TestArtifactModel>,
    val hostTestArtifacts: List<TestArtifactModel>,
    val testSuiteArtifacts: List<TestArtifactModel>,
    val testSuiteInfos: Map<String, TestSuiteInfoModel>,
    val testedTargetVariant: TestedTargetVariantModel?,
) : Serializable
