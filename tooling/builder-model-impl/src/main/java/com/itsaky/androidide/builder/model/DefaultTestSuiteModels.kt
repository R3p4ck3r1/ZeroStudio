package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.ide.JUnitEngineInfo
import com.android.builder.model.v2.ide.TestSuiteArtifact
import com.android.builder.model.v2.ide.TestSuiteTarget
import com.android.builder.model.v2.ide.TestSuiteTestInfo
import java.io.Serializable

data class DefaultJUnitEngineInfo(
    override val includedEngines: Set<String> = emptySet(),
) : JUnitEngineInfo, Serializable

data class DefaultTestSuiteTarget(
    override val name: String = "",
    override val testTaskName: String = "",
    override val targetedDevices: Collection<String> = emptyList(),
) : TestSuiteTarget, Serializable

data class DefaultTestSuiteTestInfo(
    override val junitInfo: JUnitEngineInfo = DefaultJUnitEngineInfo(),
    override val targets: Map<String, TestSuiteTarget> = emptyMap(),
) : TestSuiteTestInfo, Serializable

class DefaultTestSuiteArtifact : TestSuiteArtifact, Serializable {
  override var compileTaskName: String? = null
  override var assembleTaskName: String? = null
  override var classesFolders: Set<java.io.File> = emptySet()
  override var ideSetupTaskNames: Set<String> = emptySet()
  override var generatedSourceFolders: Collection<java.io.File> = emptyList()
  override var modelSyncFiles: Collection<Void> = emptyList()
  override var generatedClassPaths: Map<String, java.io.File> = emptyMap()
  override var bytecodeTransformations: Collection<com.android.builder.model.v2.ide.BytecodeTransformation> = emptyList()
  override var testInfo: TestSuiteTestInfo = DefaultTestSuiteTestInfo()
}
