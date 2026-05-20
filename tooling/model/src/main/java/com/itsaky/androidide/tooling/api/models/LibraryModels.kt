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
    val attributes: Map<String, String>,
    val buildType: String?,
    val capabilities: List<String>,
    val isTestFixtures: Boolean,
    val productFlavors: Map<String, String>,
    val coordinate: LibraryCoordinate?,
    val androidLibraryData: AndroidLibraryDataModel?,
) : Serializable

data class AndroidLibraryDataModel(
    val manifest: File,
    val compileJarFiles: List<File>,
    val runtimeJarFiles: List<File>,
    val resFolder: File,
    val resStaticLibrary: File,
    val assetsFolder: File,
    val jniFolder: File,
    val aidlFolder: File,
    val renderscriptFolder: File,
    val proguardRules: File,
    val externalAnnotations: File,
    val publicResources: File,
    val symbolFile: File,
) : Serializable
