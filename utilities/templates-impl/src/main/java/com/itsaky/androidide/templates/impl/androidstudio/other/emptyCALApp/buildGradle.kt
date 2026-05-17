/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.itsaky.androidide.templates.impl.androidstudio.other.emptyCalApp

import com.android.sdklib.AndroidMajorVersion
import com.android.sdklib.AndroidVersion
import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ProjectTemplateData
import com.itsaky.androidide.templates.TemplateKotlinSupport
import com.itsaky.androidide.templates.getMaterialComponentName
import com.itsaky.androidide.templates.impl.compileSdk
import com.itsaky.androidide.templates.impl.minSdk
import com.itsaky.androidide.templates.impl.targetSdk
import com.itsaky.androidide.templates.renderIf

fun buildGradle(
    projectData: ProjectTemplateData,
    packageName: String,
    buildApi: AndroidVersion,
    minApi: AndroidMajorVersion,
    targetApi: AndroidMajorVersion,
): String {
  val agpVersion = projectData.agpVersion
  val useAndroidX = projectData.androidXSupport
  val isKotlin = projectData.language == Language.Kotlin
  val useBuiltInKotlin =
      isKotlin && projectData.kotlinSupport == TemplateKotlinSupport.EXPLICIT_BUILT_IN_KOTLIN
  val useLegacyKotlinPlugin =
      isKotlin &&
          projectData.kotlinSupport == TemplateKotlinSupport.LEGACY_KOTLIN_GRADLE_PLUGIN_BEFORE_AGP9

  if (projectData.useKts) {
    return """
plugins {
    id("com.android.library")
    ${renderIf(useLegacyKotlinPlugin) { """id("org.jetbrains.kotlin.android")""" }}
    ${renderIf(useBuiltInKotlin) { """id("com.android.built-in-kotlin")""" }}
}
android {
    namespace = "$packageName"
    ${compileSdk(buildApi, agpVersion)}

    defaultConfig {
        ${minSdk(minApi, agpVersion)}
        ${targetSdk(targetApi, agpVersion)}

        testInstrumentationRunner = "${getMaterialComponentName("android.support.test.runner.AndroidJUnitRunner", useAndroidX)}"
    }
}
"""
  }

  return """
plugins {
    id 'com.android.library'
    ${renderIf(useLegacyKotlinPlugin) {"    id 'org.jetbrains.kotlin.android'"}}
    ${renderIf(useBuiltInKotlin) { "    id 'com.android.built-in-kotlin'" }}
}
android {
    namespace '$packageName'
    ${compileSdk(buildApi, agpVersion)}

    defaultConfig {
        ${minSdk(minApi, agpVersion)}
        ${targetSdk(targetApi, agpVersion)}

        testInstrumentationRunner "${getMaterialComponentName("android.support.test.runner.AndroidJUnitRunner", useAndroidX)}"
    }
}
"""
}
