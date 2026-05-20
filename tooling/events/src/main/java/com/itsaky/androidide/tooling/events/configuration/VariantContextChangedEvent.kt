package com.itsaky.androidide.tooling.events.configuration

import com.itsaky.androidide.tooling.events.OperationDescriptor
import com.itsaky.androidide.tooling.events.internal.DefaultProgressEvent
import java.io.Serializable

data class VariantContextChangedEvent(
    val projectPath: String,
    val oldVariant: String?,
    val newVariant: String,
    val clearedGeneratedSources: Boolean,
    override val displayName: String,
    override val eventTime: Long,
    override val descriptor: OperationDescriptor,
) : DefaultProgressEvent(displayName, eventTime, descriptor), Serializable
