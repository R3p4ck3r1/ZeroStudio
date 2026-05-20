package com.itsaky.androidide.tooling.api.models

import java.io.Serializable

data class AndroidProjectModelSnapshot(
    val agpVersion: String,
    val modelVersion: String,
    val moduleType: AndroidModuleType,
    val buildTypes: List<BuildTypeMatrixModel>,
    val productFlavors: List<FlavorMatrixModel>,
    val availableVariants: List<String>,
    val variantMatrix: List<VariantMatrixModel>,
    val variantContexts: Map<String, VariantContextModel>,
) : Serializable
