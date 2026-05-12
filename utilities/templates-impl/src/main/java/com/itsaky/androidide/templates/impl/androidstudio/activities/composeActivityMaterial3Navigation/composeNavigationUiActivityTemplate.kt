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

package com.itsaky.androidide.templates.impl.androidstudio.activities.composeNavigationUiActivityMaterial3

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.KOTLIN_FUNCTION
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
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

val composeNavigationUiActivityMaterial3Template
  get() = template {
    name = "Navigation UI Activity"
    description = "Create a Jetpack Compose activity with navigation UI"
    minApi = 21
    constraints =
        listOf(
            TemplateConstraint.AndroidX,
            TemplateConstraint.Kotlin,
            TemplateConstraint.Material3,
            TemplateConstraint.Compose,
        )

    category = Category.Compose
    formFactor = FormFactor.Mobile
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

    val greeting = stringParameter {
      name = "Greeting function name"
      default = "Greeting"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(UNIQUE, KOTLIN_FUNCTION)
      loggable = true
    }

    val defaultPreview = stringParameter {
      name = "Default Preview function name"
      default = "${greeting.value}Preview"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(UNIQUE, KOTLIN_FUNCTION)
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

    thumb {
      File("compose-navigation-ui-activity-material3")
          .resolve("template_compose_navigation_ui_activity_material3.png")
    }

    recipe = { data: TemplateData ->
      composeNavigationUiActivityRecipe(
          data as ModuleTemplateData,
          activityClass.value,
          packageName.value,
          isLauncher.value,
          greeting.value,
          defaultPreview.value,
      )
    }
  }
