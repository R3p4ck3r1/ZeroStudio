/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.itsaky.androidide.templates.impl.androidstudio.activities.googleAdMobAdsActivity

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.LAYOUT
import com.itsaky.androidide.templates.Constraint.NONEMPTY
import com.itsaky.androidide.templates.Constraint.UNIQUE
import com.itsaky.androidide.templates.EnumWidget
import com.itsaky.androidide.templates.FormFactor
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageNameWidget
import com.itsaky.androidide.templates.StringParameter
import com.itsaky.androidide.templates.TemplateConstraint
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.activityToLayout
import com.itsaky.androidide.templates.booleanParameter
import com.itsaky.androidide.templates.classToResource
import com.itsaky.androidide.templates.enumParameter
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.itsaky.androidide.templates.impl.androidstudio.fragments.googleAdMobAdsFragment.AdFormat
import com.itsaky.androidide.templates.layoutToActivity
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File
import java.util.Locale

val googleAdMobAdsActivityTemplate
  get() = template {
    name = "Google AdMob Ads Views Activity"
    constraints = listOf(TemplateConstraint.AndroidX)
    minApi = MIN_API
    description = "Creates an activity with AdMob Ad fragment"

    category = Category.Google
    formFactor = FormFactor.Mobile
    screens =
        listOf(
            WizardUiContext.ActivityGallery,
            WizardUiContext.MenuEntry,
            WizardUiContext.NewModule,
        )

    lateinit var layoutName: StringParameter
    val activityClass = stringParameter {
      name = "Activity Name"
      default = "MainActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { layoutToActivity(layoutName.value) }
      loggable = true
    }

    layoutName = stringParameter {
      name = "Layout Name"
      default = "activity_main"
      help = "The name of the layout to create for the activity"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
      loggable = true
    }

    val menuName = stringParameter {
      name = "Menu Resource Name"
      default = "menu_main"
      help = "The name of the resource file to create for the menu items"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { "menu_${classToResource(activityClass.value)}" }
      loggable = true
    }

    val adFormat =
        enumParameter<AdFormat> {
          name = "Ad Format"
          default = AdFormat.Interstitial
          help = "Select Interstitial Ad or Banner Ad"
        }

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = false
      help =
          "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }

    val packageName = defaultPackageNameParameter

    widgets(
        TextFieldWidget(activityClass),
        TextFieldWidget(layoutName),
        TextFieldWidget(menuName),
        EnumWidget(adFormat),
        CheckBoxWidget(isLauncher),
        PackageNameWidget(packageName),
        LanguageWidget(),
    )

    thumb {
      File("google-admob-ads-activity")
          .resolve("template_admob_activity_" + adFormat.value.name.lowercase(Locale.US) + ".png")
    }

    recipe = { data: TemplateData ->
      googleAdMobAdsActivityRecipe(
          data as ModuleTemplateData,
          activityClass.value,
          layoutName.value,
          menuName.value,
          adFormat.value,
          isLauncher.value,
          packageName.value,
      )
    }
  }
