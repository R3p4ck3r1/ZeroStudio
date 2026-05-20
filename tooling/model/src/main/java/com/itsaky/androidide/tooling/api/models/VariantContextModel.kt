package com.itsaky.androidide.tooling.api.models

import java.io.File
import java.io.Serializable

data class VariantContextModel(
    val variantName: String,
    val classpath: List<File>,
    val generatedSources: GeneratedSourceModel,
) : Serializable
