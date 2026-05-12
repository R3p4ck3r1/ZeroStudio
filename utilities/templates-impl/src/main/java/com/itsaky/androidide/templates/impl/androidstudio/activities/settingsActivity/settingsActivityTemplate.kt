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

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.NONEMPTY
import com.itsaky.androidide.templates.Constraint.UNIQUE
import com.itsaky.androidide.templates.FormFactor
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageNameWidget
import com.itsaky.androidide.templates.TemplateConstraint
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.booleanParameter
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

val settingsActivityTemplate
  get() = template {
    name = "Settings Views Activity"
    description = "Creates a new activity that allows a user to configure application settings"
    minApi = MIN_API
    constraints = listOf(TemplateConstraint.AndroidX)

    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens =
        listOf(
            WizardUiContext.ActivityGallery,
            WizardUiContext.MenuEntry,
            WizardUiContext.NewModule,
        )

    val activityClass = stringParameter {
      name = "Activity Name"
      default = "SettingsActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val multipleScreens = booleanParameter {
      name = "Split settings hierarchy into separate sub-screens"
      default = false
      help =
          "If true, this activity will have a main settings screen that links to separate settings screens"
    }

    val packageName = defaultPackageNameParameter

    widgets(
        TextFieldWidget(activityClass),
        CheckBoxWidget(multipleScreens),
        PackageNameWidget(packageName),
        LanguageWidget(),
    )

    thumb { File("settings-activity").resolve("template_settings_activity.png") }

    recipe = { data: TemplateData ->
      settingsActivityRecipe(
          data as ModuleTemplateData,
          activityClass.value,
          multipleScreens.value,
          packageName.value,
      )
    }
  }
