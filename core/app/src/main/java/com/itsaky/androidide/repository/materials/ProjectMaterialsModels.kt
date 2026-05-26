package com.itsaky.androidide.repository.materials

enum class MaterialSourceType { GRADLE_TOOLING_API, AGP_BUILDER_MODEL, SDK_TOOLING, PROJECT_FILE }

data class ProjectMaterialItem(
    val id: String,
    val title: String,
    val sourceType: MaterialSourceType,
    val apiName: String,
    val description: String,
    val path: String? = null,
)
