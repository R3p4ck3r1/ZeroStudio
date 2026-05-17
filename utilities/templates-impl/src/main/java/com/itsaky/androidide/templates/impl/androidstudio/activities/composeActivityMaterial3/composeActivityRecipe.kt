/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package com.itsaky.androidide.templates.impl.androidstudio.activities.composeActivityMaterial3

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addComposeDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.generateManifest
import com.itsaky.androidide.templates.impl.androidstudio.activities.composeActivityMaterial3.res.values.themesXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.composeActivityMaterial3.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.composeActivityMaterial3.src.app_package.ui.colorKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.composeActivityMaterial3.src.app_package.ui.themeKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.composeActivityMaterial3.src.app_package.ui.typeKt

fun RecipeExecutor.composeActivityRecipe(
    moduleData: ModuleTemplateData,
    activityClass: String,
    packageName: String,
    isLauncher: Boolean,
    greeting: String,
    defaultPreview: String,
) {
  when (moduleData.language) {
    Language.Kotlin -> Unit
    Language.Java ->
        error("Empty Compose Activity currently generates Kotlin-only sources. Please select Kotlin.")
  }
  val (_, srcOut, resOut, _) = moduleData
  addAllKotlinDependencies(moduleData)

  addDependency(mavenCoordinate = "androidx.lifecycle:lifecycle-runtime-ktx:+")
  addDependency(mavenCoordinate = "androidx.activity:activity-compose:+")

  // Add Compose dependencies, using the BOM to set versions
  addComposeDependencies(moduleData)

  addDependency(mavenCoordinate = "androidx.compose.material3:material3")

  generateManifest(
      moduleData = moduleData,
      activityClass = activityClass,
      activityThemeName = moduleData.themesData.main.name,
      packageName = packageName,
      isLauncher = isLauncher,
      hasNoActionBar = true,
      generateActivityTitle = true,
  )
  // It doesn't have to create separate themes.xml for light and night because the default
  // status bar color is same between them at this moment
  // TODO remove themes.xml once Compose library supports setting the status bar color in Composable
  // this themes.xml exists just for settings the status bar color.
  // Thus, themeName follows the non-Compose project convention.
  // (E.g. Theme.MyApplication) as opposed to the themeName variable below (E.g. MyApplicationTheme)
  mergeXml(
      themesXml(themeName = moduleData.themesData.main.name),
      resOut.resolve("values/themes.xml"),
  )

  val themeName = "${moduleData.themesData.appName}Theme"
  save(
      mainActivityKt(activityClass, defaultPreview, greeting, packageName, themeName),
      srcOut.resolve("${activityClass}.kt"),
  )
  val uiThemeFolder = "ui/theme"
  save(colorKt(packageName), srcOut.resolve("$uiThemeFolder/Color.kt"))
  save(themeKt(packageName, themeName), srcOut.resolve("$uiThemeFolder/Theme.kt"))
  save(typeKt(packageName), srcOut.resolve("$uiThemeFolder/Type.kt"))

  setJavaKotlinCompileOptions(true)
  setBuildFeature("compose", true)

  open(srcOut.resolve("${activityClass}.kt"))
}
