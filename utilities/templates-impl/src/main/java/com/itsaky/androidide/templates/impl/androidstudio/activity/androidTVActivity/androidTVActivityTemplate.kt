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

package com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.LAYOUT
import com.itsaky.androidide.templates.Constraint.NONEMPTY
import com.itsaky.androidide.templates.Constraint.UNIQUE
import com.itsaky.androidide.templates.FormFactor
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageNameWidget
import com.itsaky.androidide.templates.StringParameter
import com.itsaky.androidide.templates.TemplateConstraint
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.impl.sharedMacros.activityToLayout
import com.itsaky.androidide.templates.booleanParameter
import com.itsaky.androidide.templates.impl.defaultPackageNameParameter
import com.itsaky.androidide.templates.layoutToActivity
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

val androidTVActivityTemplate
  get() = template {
    name = "Android TV Blank Views Activity"
    minApi = 21
    description = "Creates a new Android TV activity using Leanback Support library"
    constraints = listOf(TemplateConstraint.AndroidX)

    category = Category.Activity
    formFactor = FormFactor.Tv
    screens =
        listOf(WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

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
      name = "Main Layout Name"
      default = "main"
      help = "The name of the layout to create for the activity"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
      loggable = true
    }

    val mainFragment = stringParameter {
      name = "Main Fragment"
      default = "MainFragment"
      help = "The name of the main fragment"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { "MainFragment" }
      loggable = true
    }

    val detailsActivity = stringParameter {
      name = "Details Activity"
      default = "DetailsActivity"
      help = "The name of the details activity"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { "DetailsActivity" }
      loggable = true
    }

    val detailsLayoutName = stringParameter {
      name = "Details Layout Name"
      default = "details"
      help = "The name of the layout to create for the activity"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(detailsActivity.value) }
      loggable = true
    }

    val detailsFragment = stringParameter {
      name = "Details Fragment"
      default = "VideoDetailsFragment"
      help = "The name of the details fragment"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { "VideoDetailsFragment" }
      loggable = true
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
        TextFieldWidget(mainFragment),
        TextFieldWidget(detailsActivity),
        TextFieldWidget(detailsLayoutName),
        TextFieldWidget(detailsFragment),
        CheckBoxWidget(isLauncher),
        PackageNameWidget(packageName),
        LanguageWidget(),
    )

    thumb { File("androidtv-activity").resolve("template-leanback-TV.png") }

    recipe = { data: TemplateData ->
      androidTVActivityRecipe(
          data as ModuleTemplateData,
          activityClass.value,
          layoutName.value,
          mainFragment.value,
          detailsActivity.value,
          detailsLayoutName.value,
          detailsFragment.value,
          packageName.value,
      )
    }
  }
