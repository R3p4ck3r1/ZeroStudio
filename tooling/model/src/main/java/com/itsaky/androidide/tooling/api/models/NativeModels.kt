package com.itsaky.androidide.tooling.api.models

import java.io.File
import java.io.Serializable

data class NativeAbiModel(
    val name: String,
    val sourceFlagsFile: File,
    val symbolFolderIndexFile: File,
    val buildFileIndexFile: File,
    val additionalProjectFilesIndexFile: File,
) : Serializable

data class NativeVariantModel(
    val name: String,
    val abis: List<NativeAbiModel>,
) : Serializable

data class NativeModuleModel(
    val name: String,
    val nativeBuildSystem: String,
    val ndkVersion: String,
    val defaultNdkVersion: String,
    val externalNativeBuildFile: File,
    val variants: List<NativeVariantModel>,
) : Serializable
