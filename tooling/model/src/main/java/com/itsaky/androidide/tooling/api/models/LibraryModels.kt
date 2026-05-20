package com.itsaky.androidide.tooling.api.models

import java.io.File
import java.io.Serializable

data class LibraryCoordinate(
    val group: String,
    val artifact: String,
    val version: String,
) : Serializable

data class LibraryGraphEntry(
    val key: String,
    val type: String,
    val artifact: File?,
    val lintJar: File?,
    val srcJars: List<File>,
    val docJar: File?,
    val projectPath: String?,
    val buildId: String?,
    val coordinate: LibraryCoordinate?,
) : Serializable
