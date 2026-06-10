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
package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.ide.AndroidArtifact
import com.android.builder.model.v2.ide.BytecodeTransformation
import com.android.builder.model.v2.ide.CodeShrinker
import java.io.File
import java.io.Serializable

/** @author Akash Yadav */
open class DefaultAndroidArtifact : AndroidArtifact, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Create a DefaultAndroidArtifact instance from an AndroidArtifact model.
         *
         * @param artifact The AndroidArtifact model from AGP
         * @return A DefaultAndroidArtifact instance
         */
        @JvmStatic
        fun fromArtifact(artifact: AndroidArtifact): DefaultAndroidArtifact {
            return DefaultAndroidArtifact().apply {
                this.applicationId = artifact.applicationId
                this.resGenTaskName = artifact.resGenTaskName
                this.abiFilters = artifact.abiFilters
                this.assembleTaskOutputListingFile = artifact.assembleTaskOutputListingFile

                artifact.bundleInfo?.let {
                    this.bundleInfo = DefaultBundleInfo.fromBundleInfo(it)
                }

                this.codeShrinker = artifact.codeShrinker
                this.generatedResourceFolders = artifact.generatedResourceFolders
                this.generatedAssetsFolders = artifact.generatedAssetsFolders
                this.isSigned = artifact.isSigned
                this.maxSdkVersion = artifact.maxSdkVersion

                this.minSdkVersion = DefaultApiVersion.fromApiVersion(artifact.minSdkVersion)

                this.signingConfigName = artifact.signingConfigName
                this.sourceGenTaskName = artifact.sourceGenTaskName

                artifact.testInfo?.let {
                    this.testInfo = DefaultTestInfo.fromTestInfo(it)
                }

                this.assembleTaskName = artifact.assembleTaskName
                this.classesFolders = artifact.classesFolders
                this.compileTaskName = artifact.compileTaskName
                this.generatedSourceFolders = artifact.generatedSourceFolders
                this.ideSetupTaskNames = artifact.ideSetupTaskNames

                artifact.targetSdkVersionOverride?.let {
                    this.targetSdkVersionOverride = DefaultApiVersion.fromApiVersion(it)
                }

                this.modelSyncFiles = artifact.modelSyncFiles
                this.desugaredMethodsFiles = artifact.desugaredMethodsFiles
                this.generatedClassPaths = artifact.generatedClassPaths
                this.bytecodeTransformations = artifact.bytecodeTransformations
                this.mappingR8TextFile = artifact.mappingR8TextFile
                this.mappingR8PartitionFile = artifact.mappingR8PartitionFile
            }
        }
    }

    override var applicationId: String? = ""
    override var resGenTaskName: String? = null
    override var abiFilters: Set<String>? = null
    override var assembleTaskOutputListingFile: File? = null
    override var bundleInfo: DefaultBundleInfo? = null
    override var codeShrinker: CodeShrinker? = null
    override var generatedResourceFolders: Collection<File> = emptyList()
    override var generatedAssetsFolders: Collection<File> = emptyList()
    override var isSigned: Boolean = false
    override var maxSdkVersion: Int? = null
    override var minSdkVersion: DefaultApiVersion = DefaultApiVersion()
    override var signingConfigName: String? = null
    override var sourceGenTaskName: String? = null
    override var testInfo: DefaultTestInfo? = null
    override var assembleTaskName: String? = null
    override var classesFolders: Set<File> = emptySet()
    override var compileTaskName: String? = null
    override var generatedSourceFolders: Collection<File> = emptyList()
    override var ideSetupTaskNames: Set<String> = emptySet()
    override var targetSdkVersionOverride: DefaultApiVersion? = null
    override var modelSyncFiles: Collection<Void> = emptyList()
    override var desugaredMethodsFiles: Collection<File> = emptyList()
    override var generatedClassPaths: Map<String, File> = emptyMap()
    override var bytecodeTransformations: Collection<BytecodeTransformation> = emptyList()
    override var mappingR8TextFile: File? = null
    override var mappingR8PartitionFile: File? = null
}
