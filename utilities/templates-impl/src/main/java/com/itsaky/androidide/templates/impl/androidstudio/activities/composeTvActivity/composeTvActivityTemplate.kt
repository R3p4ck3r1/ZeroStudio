/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.itsaky.androidide.templates.impl.androidstudio.activities.composeTvActivity

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.Constraint
import com.itsaky.androidide.templates.FormFactor
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageNameWidget
import com.itsaky.androidide.templates.TemplateConstraint
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.booleanParameter
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

val composeTvActivityTemplate
  get() = template {
    name = "Empty Activity"
    description = "Create a new empty activity with Compose for TV"
    minApi = 21
    constraints =
        listOf(TemplateConstraint.AndroidX, TemplateConstraint.Kotlin, TemplateConstraint.Compose)
    category = Category.TV
    formFactor = FormFactor.Tv
    screens =
        listOf(
            WizardUiContext.ActivityGallery,
            WizardUiContext.MenuEntry,
            WizardUiContext.NewProject,
            WizardUiContext.NewModule,
        )

    val activityClass = stringParameter {
      name = "Activity Name"
      default = "MainActivity"
      help = "The name of the activity class to create"
      constraints = listOf(Constraint.CLASS, Constraint.UNIQUE, Constraint.NONEMPTY)
      loggable = true
    }

    val packageName = defaultPackageNameParameter

    val isLauncher = booleanParameter {
      name = "Launcher Activity"
      default = true
      help =
          "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }

    val greeting = stringParameter {
      name = "Greeting function name"
      default = "Greeting"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(Constraint.UNIQUE, Constraint.KOTLIN_FUNCTION)
      loggable = true
    }

    val defaultPreview = stringParameter {
      name = "Default Preview function name"
      default = "${greeting.value}Preview"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(Constraint.UNIQUE, Constraint.KOTLIN_FUNCTION)
      loggable = true
    }

    widgets(
        TextFieldWidget(activityClass),
        PackageNameWidget(packageName),
        CheckBoxWidget(isLauncher),
        // Invisible widgets to pass data
        TextFieldWidget(greeting),
        TextFieldWidget(defaultPreview),
        LanguageWidget(),
    )

    thumb { File("compose-tv-activity").resolve("template_compose_tv_empty_activity.png") }

    recipe = { data: TemplateData ->
      composeTvActivityRecipe(
          moduleData = data as ModuleTemplateData,
          activityClass = activityClass.value,
          packageName = packageName.value,
          isLauncher = isLauncher.value,
          greeting = greeting.value,
          defaultPreview = defaultPreview.value,
      )
    }
  }
