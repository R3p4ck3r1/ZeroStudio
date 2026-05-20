package com.itsaky.androidide.builder.model

import java.io.Serializable

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
