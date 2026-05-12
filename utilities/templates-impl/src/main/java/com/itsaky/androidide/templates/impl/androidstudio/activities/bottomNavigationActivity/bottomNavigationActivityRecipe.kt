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

package com.itsaky.androidide.templates.impl.androidstudio.activities.bottomNavigationActivity

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.impl.androidstudio.activities.bottomNavigationActivity.res.layout.navigationActivityMainXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.bottomNavigationActivity.res.menu.navigationXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.bottomNavigationActivity.res.navigation.mobileNavigationXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.bottomNavigationActivity.res.values.dimensXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.bottomNavigationActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.bottomNavigationActivity.src.app_package.mainActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activities.bottomNavigationActivity.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addAllKotlinDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addMaterialDependency
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addViewBindingSupport
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.generateManifest
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.navigation.navigationDependencies
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.navigation.saveFragmentAndViewModel
import java.io.File

fun RecipeExecutor.bottomNavigationActivityRecipe(
    moduleData: ModuleTemplateData,
    activityClass: String,
    layoutName: String,
    packageName: String,
    navGraphName: String,
) {
  val (projectData, srcOut, resOut) = moduleData
  val appCompatVersion = moduleData.apis.appCompatVersion
  val useAndroidX = moduleData.projectTemplateData.androidXSupport
  val ktOrJavaExt = projectData.language.extension
  val generateKotlin = projectData.language == Language.Kotlin
  val isLauncher = moduleData.isNewModule
  addAllKotlinDependencies(moduleData)

  addDependency("com.android.support:appcompat-v7:${appCompatVersion}.+")
  addDependency("com.android.support.constraint:constraint-layout:+")
  addMaterialDependency(useAndroidX)
  addViewBindingSupport(moduleData.viewBindingSupport, true)

  if (moduleData.apis.minApi.apiLevel < 21) {
    addDependency("com.android.support:support-vector-drawable:${appCompatVersion}.+")
  }

  generateManifest(
      moduleData,
      activityClass,
      packageName,
      isLauncher,
      hasNoActionBar = false,
      generateActivityTitle = true,
  )

  val language = projectData.language
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  saveFragmentAndViewModel(
      resOut = resOut,
      srcOut = srcOut,
      language = language,
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      fragmentPrefix = "home",
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported,
  )
  saveFragmentAndViewModel(
      resOut = resOut,
      srcOut = srcOut,
      language = language,
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      fragmentPrefix = "dashboard",
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported,
  )
  saveFragmentAndViewModel(
      resOut = resOut,
      srcOut = srcOut,
      language = language,
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      fragmentPrefix = "notifications",
      useAndroidX = useAndroidX,
      isViewBindingSupported = isViewBindingSupported,
  )
  navigationDependencies(generateKotlin, useAndroidX, moduleData.apis.appCompatVersion)
  if (generateKotlin) {
    setJavaKotlinCompileOptions(true)
  }

  save(
      mobileNavigationXml(navGraphName = navGraphName, packageName = packageName),
      resOut.resolve("navigation/${navGraphName}.xml"),
  )
  open(resOut.resolve("navigation/${navGraphName}.xml"))

  copy(File("bottom-navigation-activity").resolve("drawable"), resOut.resolve("drawable"))

  // navHostFragmentId needs to be unique, thus appending layoutName since it's
  // guaranteed to be unique
  val navHostFragmentId = "nav_host_fragment_${layoutName}"
  val mainActivity =
      when (projectData.language) {
        Language.Java ->
            mainActivityJava(
                activityClass = activityClass,
                layoutName = layoutName,
                navHostFragmentId = navHostFragmentId,
                packageName = packageName,
                applicationPackage = projectData.applicationPackage,
                useAndroidX = useAndroidX,
                isViewBindingSupported = isViewBindingSupported,
            )
        Language.Kotlin ->
            mainActivityKt(
                activityClass = activityClass,
                layoutName = layoutName,
                navHostFragmentId = navHostFragmentId,
                packageName = packageName,
                applicationPackage = projectData.applicationPackage,
                useAndroidX = useAndroidX,
                isViewBindingSupported = isViewBindingSupported,
            )
      }

  save(mainActivity, srcOut.resolve("${activityClass}.${ktOrJavaExt}"))
  save(
      navigationActivityMainXml(
          navGraphName = navGraphName,
          navHostFragmentId = navHostFragmentId,
          useAndroidX = useAndroidX,
      ),
      resOut.resolve("layout/${layoutName}.xml"),
  )

  mergeXml(dimensXml(), resOut.resolve("values/dimens.xml"))
  mergeXml(stringsXml(), resOut.resolve("values/strings.xml"))
  mergeXml(navigationXml(), resOut.resolve("menu/bottom_nav_menu.xml"))

  open(resOut.resolve("layout/${layoutName}.xml"))
}
