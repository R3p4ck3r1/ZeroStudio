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

import com.android.builder.model.v2.ide.ComponentInfo
import com.android.builder.model.v2.ide.ProjectInfo
import com.android.builder.model.v2.models.ProjectGraph
import java.io.Serializable

/**
 * Default implementation of [ComponentInfo] for AGP v2.
 *
 * @author android_zero
 */
class DefaultComponentInfo(
    override val attributes: Map<String, String>,
    override val buildType: String?,
    override val capabilities: List<String>,
    override val isTestFixtures: Boolean,
    override val productFlavors: Map<String, String>,
) : ComponentInfo, Serializable {
    companion object {
        private const val serialVersionUID = 1L

        /**
         * Create a DefaultComponentInfo instance from a ComponentInfo model.
         *
         * @param componentInfo The ComponentInfo model from AGP
         * @return A DefaultComponentInfo instance
         */
        @JvmStatic
        fun fromComponentInfo(componentInfo: ComponentInfo): DefaultComponentInfo {
            return DefaultComponentInfo(
                attributes = componentInfo.attributes,
                buildType = componentInfo.buildType,
                capabilities = componentInfo.capabilities,
                isTestFixtures = componentInfo.isTestFixtures,
                productFlavors = componentInfo.productFlavors
            )
        }
    }

    private val serialVersionUID = 1L
}

/**
 * Default implementation of [ProjectGraph] for AGP v2.
 *
 * Provides the project dependency graph for AGP 9.x+.
 *
 * @author android_zero
 */
class DefaultProjectGraph : ProjectGraph, Serializable {
    companion object {
        private const val serialVersionUID = 1L

        /**
         * Create a DefaultProjectGraph instance from a ProjectGraph model.
         *
         * @param projectGraph The ProjectGraph model from AGP
         * @return A DefaultProjectGraph instance
         */
        @JvmStatic
        fun fromProjectGraph(projectGraph: ProjectGraph): DefaultProjectGraph {
            return DefaultProjectGraph().apply {
                // Copy resolved variants (deprecated but still provided for backward compatibility)
                this.resolvedVariants = projectGraph.resolvedVariants

                // Copy resolved variants with project info (new in AGP 9.x)
                this.resolvedVariantsWithProjectInfo = projectGraph.resolvedVariantsWithProjectInfo?.mapKeys { (projectInfo, _) ->
                    DefaultProjectInfo.fromProjectInfo(projectInfo)
                }
            }
        }

        /**
         * Create an empty DefaultProjectGraph instance.
         * Used as fallback for AGP 8.x where ProjectGraph is not available.
         *
         * @return An empty DefaultProjectGraph instance
         */
        @JvmStatic
        fun empty(): DefaultProjectGraph {
            return DefaultProjectGraph().apply {
                this.resolvedVariants = emptyMap()
                this.resolvedVariantsWithProjectInfo = emptyMap()
            }
        }
    }

    override var resolvedVariants: Map<String, String>? = null
    override var resolvedVariantsWithProjectInfo: Map<ProjectInfo, String>? = emptyMap()

    private val serialVersionUID = 1L
}
