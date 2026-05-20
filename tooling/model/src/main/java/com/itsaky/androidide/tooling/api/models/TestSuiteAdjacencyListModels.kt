package com.itsaky.androidide.tooling.api.models

import java.io.Serializable

data class ArtifactDependenciesAdjacencyListModel(
    val edges: List<DependencyEdgeModel>,
) : Serializable

data class DependencyEdgeModel(
    val from: String,
    val to: String,
) : Serializable

data class TestSuiteSourceDependenciesAdjacencyListModel(
    val sourceType: String,
    val sourceName: String,
    val artifactDependencies: ArtifactDependenciesAdjacencyListModel,
) : Serializable

data class TestSuiteDependenciesAdjacencyListModel(
    val suiteName: String,
    val sourcesDependencies: List<TestSuiteSourceDependenciesAdjacencyListModel>,
) : Serializable
