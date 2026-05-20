package com.itsaky.androidide.tooling.events.configuration

import com.itsaky.androidide.tooling.events.OperationDescriptor
import com.itsaky.androidide.tooling.events.internal.DefaultProgressEvent
import java.io.File
import java.io.Serializable

data class ManifestMergerParsedPermission(
    val permission: String,
    val source: String,
) : Serializable

data class ManifestMergerParsedEvent(
    val projectPath: String,
    val variant: String,
    val reportFile: File,
    val permissions: List<ManifestMergerParsedPermission>,
    override val displayName: String,
    override val eventTime: Long,
    override val descriptor: OperationDescriptor,
) : DefaultProgressEvent(displayName, eventTime, descriptor), Serializable
