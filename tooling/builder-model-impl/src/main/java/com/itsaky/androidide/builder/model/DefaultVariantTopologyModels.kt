package com.itsaky.androidide.builder.model

import java.io.File
import java.io.Serializable

data class DefaultFlavorMatrix(
    val name: String,
    val dimension: String?,
    val minSdkVersion: Int?,
    val targetSdkVersion: Int?,
    val resourceConfigurations: List<String>,
) : Serializable

data class DefaultBuildTypeMatrix(
    val name: String,
    val isDebuggable: Boolean,
    val isMinifyEnabled: Boolean,
    val signingConfig: String?,
) : Serializable

data class DefaultGeneratedSources(
    val annotationProcessorSources: List<File>,
    val buildConfigSources: List<File>,
    val viewBindingSources: List<File>,
    val dataBindingSources: List<File>,
) : Serializable
