/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.tooling.api.models

import java.io.File
import java.io.Serializable

/** Serializable snapshot of Gradle's BuildEnvironment model. */
data class GradleBuildEnvironment(
    val buildRootDir: File?,
    val gradle: GradleRuntimeEnvironment?,
    val java: GradleJavaEnvironment?,
    val versionInfo: String? = null,
) : Serializable {

    private val serialVersionUID = 1L
}

data class GradleRuntimeEnvironment(
    val gradleUserHome: File?,
    val gradleVersion: String?,
) : Serializable {

    private val serialVersionUID = 1L
}

data class GradleJavaEnvironment(
    val javaHome: File?,
    val jvmArguments: List<String> = emptyList(),
) : Serializable {

    private val serialVersionUID = 1L
}
