/*
 *  This file is part of AndroidIDE.
 */

package com.itsaky.androidide.tooling.api.messages

import java.io.Serializable
import org.gradle.tooling.events.OperationType

/**
 * Generic execution request for build invocations.
 */
data class ExecutionRequest(
    val tasks: List<String> = emptyList(),
    val arguments: List<String> = emptyList(),
    val jvmArguments: List<String> = emptyList(),
    val operationTypes: Set<OperationType> = emptySet(),
) : Serializable

