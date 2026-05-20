package com.itsaky.androidide.tooling.api.models

import java.io.Serializable

data class TestSuiteSourceAdjacencyModel(
    val sourceType: String,
    val sourceName: String,
    val dependencies: ArtifactDependencyAdjacencyModel,
) : Serializable

data class TestSuiteAdjacencyModel(
    val suiteName: String,
    val sources: List<TestSuiteSourceAdjacencyModel>,
) : Serializable
