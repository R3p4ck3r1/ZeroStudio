package com.itsaky.androidide.tooling.api.models

import java.io.File
import java.io.Serializable

data class MergedPermissionSource(
    val permission: String,
    val source: String,
    val tagName: String = "uses-permission",
) : Serializable

data class ManifestMergerReport(
    val reportFile: File,
    val mergedPermissions: List<MergedPermissionSource>,
) : Serializable
