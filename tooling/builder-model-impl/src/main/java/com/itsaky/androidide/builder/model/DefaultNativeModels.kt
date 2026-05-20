package com.itsaky.androidide.builder.model

import java.io.File
import java.io.Serializable

data class DefaultNativeAbi(
    val name: String,
    val sourceFlagsFile: File?,
    val symbolFolderIndexFile: File?,
    val buildFileIndexFile: File?,
    val additionalProjectFilesIndexFile: File?,
) : Serializable

data class DefaultNativeVariant(
    val name: String,
    val abis: List<DefaultNativeAbi>,
) : Serializable

data class DefaultNativeModule(
    val name: String,
    val nativeBuildSystem: String,
    val ndkVersion: String?,
    val defaultNdkVersion: String?,
    val externalNativeBuildFile: File?,
    val variants: List<DefaultNativeVariant>,
) : Serializable
