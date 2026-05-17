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

package com.itsaky.androidide.templates.impl.androidstudio.activities.composeWearActivity

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.Constraint
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.NONEMPTY
import com.itsaky.androidide.templates.Constraint.UNIQUE
import com.itsaky.androidide.templates.FormFactor
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

val composeWearActivityTemplate
  get() = template {
    name = "Empty Wear App"
    minApi = 30
    description = "Creates an empty app using Compose for Wear OS"

    constraints =
        listOf(TemplateConstraint.AndroidX, TemplateConstraint.Kotlin, TemplateConstraint.Compose)
    category = Category.Wear
    formFactor = FormFactor.Wear
    screens =
        listOf(WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

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

    val wearAppName = stringParameter {
      name = "WearApp function name"
      default = "WearApp"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
      loggable = true
    }

    val defaultPreview = stringParameter {
      name = "Default Preview function name"
      default = "DefaultPreview"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
      loggable = true
    }

    widgets(
        TextFieldWidget(activityClass),
        PackageNameWidget(packageName),
        CheckBoxWidget(isLauncher),
        LanguageWidget(),
        // Invisible widgets to pass data
        TextFieldWidget(defaultPreview),
    )

    thumb { File("compose-wear-activity").resolve("templates-wear-app.png") }

    recipe = { data: TemplateData ->
      composeWearActivityRecipe(
          data as ModuleTemplateData,
          activityClass.value,
          packageName.value,
          isLauncher.value,
          wearAppName.value,
          defaultPreview.value,
      )
    }
  }

val composeWearActivityWithTileAndComplicationTemplate
  get() = template {
    name = "Empty Wear App With Tile And Complication"
    minApi = 30
    description =
        "Creates an empty app using Compose for Wear OS, including a Tile and Complication"

    constraints =
        listOf(TemplateConstraint.AndroidX, TemplateConstraint.Kotlin, TemplateConstraint.Compose)
    category = Category.Wear
    formFactor = FormFactor.Wear
    screens =
        listOf(WizardUiContext.MenuEntry, WizardUiContext.NewProject, WizardUiContext.NewModule)

    val activityClass = stringParameter {
      name = "Activity Name"
      default = "MainActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }
    val tileServiceClass = stringParameter {
      name = "Tile Service Name"
      default = "MainTileService"
      help = "The name of the tile service class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }
    val complicationServiceClass = stringParameter {
      name = "Complication Service Name"
      default = "MainComplicationService"
      help = "The name of the complication service class to create"
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

    val wearAppName = stringParameter {
      name = "WearApp function name"
      default = "WearApp"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
      loggable = true
    }

    val defaultPreview = stringParameter {
      name = "Default Preview function name"
      default = "DefaultPreview"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
      loggable = true
    }
    val tilePreview = stringParameter {
      name = "Tile Default Preview function name"
      default = "tilePreview"
      help = "Used for deduplication"
      visible = { false }
      constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
      loggable = true
    }

    widgets(
        TextFieldWidget(activityClass),
        TextFieldWidget(tileServiceClass),
        TextFieldWidget(complicationServiceClass),
        PackageNameWidget(packageName),
        CheckBoxWidget(isLauncher),
        LanguageWidget(),
        // Invisible widgets to pass data
        TextFieldWidget(defaultPreview),
        TextFieldWidget(tilePreview),
    )

    thumb { File("compose-wear-activity").resolve("templates-wear-app-with-tile-complication.png") }

    recipe = { data: TemplateData ->
      composeWearActivityWithTileAndComplicationRecipe(
          data as ModuleTemplateData,
          activityClass.value,
          tileServiceClass.value,
          tilePreview.value,
          complicationServiceClass.value,
          packageName.value,
          isLauncher.value,
          wearAppName.value,
          defaultPreview.value,
      )
    }
  }
