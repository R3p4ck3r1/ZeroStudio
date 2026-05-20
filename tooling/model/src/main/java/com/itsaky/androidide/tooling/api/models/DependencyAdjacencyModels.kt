package com.itsaky.androidide.tooling.api.models

import java.io.Serializable

data class GraphNodeModel(
    val key: String,
    val requestedCoordinates: String?,
    val dependencies: List<String>,
) : Serializable

data class ArtifactDependencyAdjacencyModel(
    val compile: List<GraphNodeModel>,
    val runtime: List<GraphNodeModel>,
) : Serializable

data class VariantDependencyAdjacencyModel(
    val mainArtifact: ArtifactDependencyAdjacencyModel,
    val deviceTestArtifacts: Map<String, ArtifactDependencyAdjacencyModel>,
    val hostTestArtifacts: Map<String, ArtifactDependencyAdjacencyModel>,
) : Serializable
