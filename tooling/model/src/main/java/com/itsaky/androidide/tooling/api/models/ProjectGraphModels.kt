package com.itsaky.androidide.tooling.api.models

import java.io.Serializable

data class ProjectVariantResolutionModel(
    val buildId: String,
    val projectPath: String,
    val variantName: String,
    val attributes: Map<String, String>,
    val buildType: String?,
    val productFlavors: Map<String, String>,
    val capabilities: List<String>,
    val isTestFixtures: Boolean,
) : Serializable


data class ProjectInfoNodeModel(
    val buildId: String,
    val projectPath: String,
    val selectedVariant: String?,
    val attributes: Map<String, String>,
    val buildType: String?,
    val productFlavors: Map<String, String>,
    val capabilities: List<String>,
    val isTestFixtures: Boolean,
) : Serializable
