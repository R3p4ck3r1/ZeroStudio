package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.ide.JUnitEngineInfo
import com.android.builder.model.v2.ide.TestSuiteArtifact
import com.android.builder.model.v2.ide.TestSuiteTarget
import com.android.builder.model.v2.ide.TestSuiteTestInfo
import java.io.Serializable

data class DefaultJUnitEngineInfo(
    override val includedEngines: Set<String> = emptySet(),
) : JUnitEngineInfo, Serializable {
    companion object {
        private const val serialVersionUID = 1L
        
        /**
         * Create a DefaultJUnitEngineInfo instance from a JUnitEngineInfo model.
         *
         * @param junitInfo The JUnitEngineInfo model from AGP
         * @return A DefaultJUnitEngineInfo instance
         */
        @JvmStatic
        fun fromJUnitEngineInfo(junitInfo: JUnitEngineInfo): DefaultJUnitEngineInfo {
            return DefaultJUnitEngineInfo(
                includedEngines = junitInfo.includedEngines
            )
        }
    }
}

data class DefaultTestSuiteTarget(
    override val name: String = "",
    override val testTaskName: String = "",
    override val targetedDevices: Collection<String> = emptyList(),
) : TestSuiteTarget, Serializable {
    companion object {
        private const val serialVersionUID = 1L
        
        /**
         * Create a DefaultTestSuiteTarget instance from a TestSuiteTarget model.
         *
         * @param target The TestSuiteTarget model from AGP
         * @return A DefaultTestSuiteTarget instance
         */
        @JvmStatic
        fun fromTestSuiteTarget(target: TestSuiteTarget): DefaultTestSuiteTarget {
            return DefaultTestSuiteTarget(
                name = target.name,
                testTaskName = target.testTaskName,
                targetedDevices = target.targetedDevices
            )
        }
    }
}

data class DefaultTestSuiteTestInfo(
    override val junitInfo: JUnitEngineInfo = DefaultJUnitEngineInfo(),
    override val targets: Map<String, TestSuiteTarget> = emptyMap(),
) : TestSuiteTestInfo, Serializable {
    companion object {
        private const val serialVersionUID = 1L
        
        /**
         * Create a DefaultTestSuiteTestInfo instance from a TestSuiteTestInfo model.
         *
         * @param testInfo The TestSuiteTestInfo model from AGP
         * @return A DefaultTestSuiteTestInfo instance
         */
        @JvmStatic
        fun fromTestSuiteTestInfo(testInfo: TestSuiteTestInfo): DefaultTestSuiteTestInfo {
            return DefaultTestSuiteTestInfo(
                junitInfo = DefaultJUnitEngineInfo.fromJUnitEngineInfo(testInfo.junitInfo),
                targets = testInfo.targets.mapValues { (_, target) ->
                    DefaultTestSuiteTarget.fromTestSuiteTarget(target)
                }
            )
        }
    }
}

class DefaultTestSuiteArtifact : TestSuiteArtifact, Serializable {
    companion object {
        private const val serialVersionUID = 1L
        
        /**
         * Create a DefaultTestSuiteArtifact instance from a TestSuiteArtifact model.
         *
         * @param artifact The TestSuiteArtifact model from AGP
         * @return A DefaultTestSuiteArtifact instance
         */
        @JvmStatic
        fun fromArtifact(artifact: TestSuiteArtifact): DefaultTestSuiteArtifact {
            return DefaultTestSuiteArtifact().apply {
                this.compileTaskName = artifact.compileTaskName
                this.assembleTaskName = artifact.assembleTaskName
                this.classesFolders = artifact.classesFolders
                this.ideSetupTaskNames = artifact.ideSetupTaskNames
                this.generatedSourceFolders = artifact.generatedSourceFolders
                this.modelSyncFiles = artifact.modelSyncFiles
                this.generatedClassPaths = artifact.generatedClassPaths
                this.bytecodeTransformations = artifact.bytecodeTransformations
                this.testInfo = DefaultTestSuiteTestInfo.fromTestSuiteTestInfo(artifact.testInfo)
            }
        }
    }
    
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
