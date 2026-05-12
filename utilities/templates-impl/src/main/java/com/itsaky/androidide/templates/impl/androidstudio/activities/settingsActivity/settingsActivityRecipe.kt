/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.activityToLayout
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addMaterialDependency
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.generateManifest
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.res.layout.settingsActivityXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.res.values.arraysXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.res.xml.headerPreferencesXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.res.xml.messagesPreferencesXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.res.xml.rootPreferencesXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.res.xml.syncPreferencesXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.src.app_package.multipleScreenSettingsActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.src.app_package.multipleScreenSettingsActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.src.app_package.singleScreenSettingsActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.settingsActivity.src.app_package.singleScreenSettingsActivityKt
import java.io.File

fun RecipeExecutor.settingsActivityRecipe(
    moduleData: ModuleTemplateData,
    activityClass: String,
    multipleScreens: Boolean,
    packageName: String,
) {
  val (projectData, srcOut, resOut, _) = moduleData
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  addAllKotlinDependencies(moduleData)

  val simpleName = activityToLayout(activityClass)
  addDependency("com.android.support:appcompat-v7:${moduleData.apis.appCompatVersion}.+")
  addDependency("androidx.preference:preference:+")
  addMaterialDependency(useAndroidX)

  generateManifest(
      moduleData,
      activityClass,
      packageName,
      isLauncher = moduleData.isNewModule,
      hasNoActionBar = false,
      generateActivityTitle = true,
  )

  mergeXml(stringsXml(activityClass, simpleName), resOut.resolve("values/strings.xml"))
  mergeXml(arraysXml(), resOut.resolve("values/arrays.xml"))
  mergeXml(settingsActivityXml(), resOut.resolve("layout/settings_activity.xml"))

  if (multipleScreens) {
    copy(File("settings-activity").resolve("drawable"), resOut.resolve("drawable"))

    mergeXml(
        headerPreferencesXml(activityClass, packageName),
        resOut.resolve("xml/header_preferences.xml"),
    )
    mergeXml(messagesPreferencesXml(), resOut.resolve("xml/messages_preferences.xml"))
    mergeXml(syncPreferencesXml(), resOut.resolve("xml/sync_preferences.xml"))
    val multipleScreenSettingsActivity =
        when (projectData.language) {
          Language.Java ->
              multipleScreenSettingsActivityJava(activityClass, packageName, simpleName)
          Language.Kotlin ->
              multipleScreenSettingsActivityKt(activityClass, packageName, simpleName)
        }
    save(multipleScreenSettingsActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  } else {
    mergeXml(rootPreferencesXml(), resOut.resolve("xml/root_preferences.xml"))
    val singleScreenSettingsActivity =
        when (projectData.language) {
          Language.Java -> singleScreenSettingsActivityJava(activityClass, packageName)
          Language.Kotlin -> singleScreenSettingsActivityKt(activityClass, packageName)
        }
    save(singleScreenSettingsActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  }
  open(srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
}
