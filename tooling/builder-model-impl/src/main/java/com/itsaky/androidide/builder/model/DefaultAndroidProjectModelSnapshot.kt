package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.ide.ProjectInfo
import java.io.Serializable

/** Snapshot-friendly description of a resolved project variant. */
data class DefaultProjectVariantResolution(
    val buildId: String,
    val projectPath: String,
    val variantName: String,
    val buildType: String?,
    val productFlavors: Map<String, String>,
    val attributes: Map<String, String>,
    val capabilities: List<String>,
    val isTestFixtures: Boolean,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun fromProjectInfo(projectInfo: ProjectInfo, variantName: String): DefaultProjectVariantResolution {
            return DefaultProjectVariantResolution(
                buildId = projectInfo.buildId,
                projectPath = projectInfo.projectPath,
                variantName = variantName,
                buildType = projectInfo.buildType,
                productFlavors = projectInfo.productFlavors,
                attributes = projectInfo.attributes,
                capabilities = projectInfo.capabilities,
                isTestFixtures = projectInfo.isTestFixtures,
            )
        }
    }
}

/** Snapshot-friendly project identity node for project graph consumers. */
data class DefaultProjectInfoNode(
    val buildId: String,
    val projectPath: String,
    val buildType: String?,
    val productFlavors: Map<String, String>,
    val attributes: Map<String, String>,
    val capabilities: List<String>,
    val isTestFixtures: Boolean,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun fromProjectInfo(projectInfo: ProjectInfo): DefaultProjectInfoNode {
            return DefaultProjectInfoNode(
                buildId = projectInfo.buildId,
                projectPath = projectInfo.projectPath,
                buildType = projectInfo.buildType,
                productFlavors = projectInfo.productFlavors,
                attributes = projectInfo.attributes,
                capabilities = projectInfo.capabilities,
                isTestFixtures = projectInfo.isTestFixtures,
            )
        }
    }
}

data class DefaultAndroidProjectModelSnapshot(
    val agpVersion: String,
    val modelVersion: String,
    val moduleType: String,
    val buildTypes: List<DefaultBuildTypeMatrix>,
    val productFlavors: List<DefaultFlavorMatrix>,
    val availableVariants: List<String>,
    val variantMatrix: List<DefaultVariantMatrix>,
    val variantContexts: Map<String, DefaultVariantContext>,
    val resolvedProjectVariants: Map<String, String>,
    val resolvedProjectVariantDetails: List<DefaultProjectVariantResolution>,
    val projectInfoNodes: List<DefaultProjectInfoNode>,
    val nativeModule: DefaultNativeModule?,
) : Serializable
