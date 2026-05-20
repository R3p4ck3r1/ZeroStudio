package com.itsaky.androidide.tooling.events.configuration

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
) : Serializable
