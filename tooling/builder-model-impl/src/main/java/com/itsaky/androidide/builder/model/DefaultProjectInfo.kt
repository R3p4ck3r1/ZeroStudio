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

import com.android.builder.model.v2.ide.ProjectInfo
import java.io.Serializable

/** @author Akash Yadav */
data class DefaultProjectInfo(
    override val attributes: Map<String, String>,
    override val buildType: String?,
    override val capabilities: List<String>,
    override val isTestFixtures: Boolean,
    override val productFlavors: Map<String, String>,
    override val buildId: String,
    override val projectPath: String,
) : ProjectInfo, Serializable {
    companion object {
        private const val serialVersionUID = 1L
        
        /**
         * Create a DefaultProjectInfo instance from a ProjectInfo model.
         *
         * @param projectInfo The ProjectInfo model from AGP
         * @return A DefaultProjectInfo instance
         */
        @JvmStatic
        fun fromProjectInfo(projectInfo: ProjectInfo): DefaultProjectInfo {
            return DefaultProjectInfo(
                attributes = projectInfo.attributes,
                buildType = projectInfo.buildType,
                capabilities = projectInfo.capabilities,
                isTestFixtures = projectInfo.isTestFixtures,
                productFlavors = projectInfo.productFlavors,
                buildId = projectInfo.buildId,
                projectPath = projectInfo.projectPath
            )
        }
    }
    
    private val serialVersionUID = 1L
}
