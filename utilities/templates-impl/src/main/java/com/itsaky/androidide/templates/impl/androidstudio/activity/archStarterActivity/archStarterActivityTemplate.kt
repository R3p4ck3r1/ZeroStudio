/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.itsaky.androidide.templates.impl.androidstudio.activity.archStarterActivity

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
import com.itsaky.androidide.templates.TemplateFlag
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.booleanParameter
import com.itsaky.androidide.templates.impl.defaultPackageNameParameter
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

val archStarterActivityTemplate
  get() = template {
    name = "Architecture Sample"
    description = "Create a new activity based on recommended Android architecture"
    minApi = 21
    constraints =
        listOf(
            TemplateConstraint.AndroidX,
            TemplateConstraint.Kotlin,
            TemplateConstraint.Material3,
            TemplateConstraint.Compose,
        )
    category = Category.Application
    flags = listOf(TemplateFlag.NewProjectAgent)
    formFactor = FormFactor.Mobile
    screens =
        listOfNotNull(
            // Only used for Gemini-based project creation for now, and for testing.
            WizardUiContext.NewProject
        )

    val activityClass = stringParameter {
      name = "Activity Name"
      default = "MainActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val packageName = defaultPackageNameParameter

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = false
      help =
          "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }

    widgets(
        TextFieldWidget(activityClass),
        PackageNameWidget(packageName),
        CheckBoxWidget(isLauncher),
        LanguageWidget(),
    )

    thumb {
      File("compose-activity-material3").resolve("template_compose_empty_activity_material3.png")
    }

    recipe = { data: TemplateData ->
      archStarterActivityRecipe(
          data as ModuleTemplateData,
          activityClass.value,
          packageName.value,
          isLauncher.value,
      )
    }
  }
