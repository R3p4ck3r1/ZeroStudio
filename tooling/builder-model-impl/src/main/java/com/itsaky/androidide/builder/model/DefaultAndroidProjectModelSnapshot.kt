package com.itsaky.androidide.builder.model

import java.io.Serializable

data class DefaultAndroidProjectModelSnapshot(
    val moduleType: String,
    val buildTypes: List<DefaultBuildTypeMatrix>,
    val productFlavors: List<DefaultFlavorMatrix>,
    val availableVariants: List<String>,
) : Serializable
