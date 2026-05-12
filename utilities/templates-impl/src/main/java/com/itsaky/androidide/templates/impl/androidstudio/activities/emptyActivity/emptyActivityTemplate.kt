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
package com.itsaky.androidide.templates.impl.androidstudio.activities.emptyActivity

import com.itsaky.androidide.templates.BooleanParameter
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
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.activityToLayout
import com.itsaky.androidide.templates.booleanParameter
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.itsaky.androidide.templates.layoutToActivity
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

val emptyActivityTemplate
  get() = template {
    name = "Empty Views Activity"
    minApi = MIN_API
    description = "Creates a new empty activity"

    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens =
        listOf(
            WizardUiContext.ActivityGallery,
            WizardUiContext.MenuEntry,
            WizardUiContext.NewProject,
            WizardUiContext.NewModule,
        )
    constraints = listOf(TemplateConstraint.AndroidX, TemplateConstraint.Material3)

    val generateLayout: BooleanParameter = booleanParameter {
      name = "Generate a Layout File"
      default = true
      help = "If true, a layout file will be generated"
    }
    lateinit var layoutName: StringParameter
    val activityClass: StringParameter = stringParameter {
      name = "Activity Name"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { layoutToActivity(layoutName.value) }
      default = "MainActivity"
      help = "The name of the activity class to create"
      loggable = true
    }
    layoutName = stringParameter {
      name = "Layout Name"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
      default = "activity_main"
      visible = { generateLayout.value }
      help = "The name of the UI layout to create for the activity"
      loggable = true
    }
    val isLauncher: BooleanParameter = booleanParameter {
      name = "Launcher Activity"
      visible = { !isNewModule }
      default = false
      help =
          "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }
    val packageName = defaultPackageNameParameter

    widgets(
        TextFieldWidget(activityClass),
        CheckBoxWidget(generateLayout),
        TextFieldWidget(layoutName),
        CheckBoxWidget(isLauncher),
        PackageNameWidget(packageName),
        LanguageWidget(),
    )

    thumb { File("empty-activity").resolve("template_empty_activity.png") }

    recipe = { data ->
      generateEmptyActivity(
          data as ModuleTemplateData,
          activityClass.value,
          generateLayout.value,
          layoutName.value,
          isLauncher.value,
          packageName.value,
      )
    }
  }
