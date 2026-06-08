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

import com.android.builder.model.v2.ide.BytecodeTransformation
import com.android.builder.model.v2.ide.JavaArtifact
import java.io.File
import java.io.Serializable

/** @author Akash Yadav */
class DefaultJavaArtifact : JavaArtifact, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        /**
         * Create a DefaultJavaArtifact instance from a JavaArtifact model.
         *
         * @param artifact The JavaArtifact model from AGP
         * @return A DefaultJavaArtifact instance
         */
        @JvmStatic
        fun fromJavaArtifact(artifact: JavaArtifact): DefaultJavaArtifact {
            return DefaultJavaArtifact().apply {
                this.assembleTaskName = artifact.assembleTaskName
                this.classesFolders = artifact.classesFolders
                this.compileTaskName = artifact.compileTaskName
                this.generatedSourceFolders = artifact.generatedSourceFolders
                this.ideSetupTaskNames = artifact.ideSetupTaskNames
                this.mockablePlatformJar = artifact.mockablePlatformJar
                this.runtimeResourceFolder = artifact.runtimeResourceFolder
                this.modelSyncFiles = artifact.modelSyncFiles
                this.generatedClassPaths = artifact.generatedClassPaths
                this.bytecodeTransformations = artifact.bytecodeTransformations
            }
        }
    }

    override var modelSyncFiles: Collection<Void> = emptyList()

    override var assembleTaskName: String? = null
    override var classesFolders: Set<File> = emptySet()
    override var compileTaskName: String? = null
    override var generatedSourceFolders: Collection<File> = emptyList()
    override var ideSetupTaskNames: Set<String> = emptySet()
    override var mockablePlatformJar: File? = null
    override var runtimeResourceFolder: File? = null
    override var generatedClassPaths: Map<String, File> = emptyMap()
    override var bytecodeTransformations: Collection<BytecodeTransformation> = emptyList()
}
