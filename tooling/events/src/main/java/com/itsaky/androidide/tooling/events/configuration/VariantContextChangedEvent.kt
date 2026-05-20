package com.itsaky.androidide.tooling.events.configuration

import java.io.Serializable

data class VariantContextChangedEvent(
    val projectPath: String,
    val oldVariant: String?,
    val newVariant: String,
    val clearedGeneratedSources: Boolean,
) : Serializable
