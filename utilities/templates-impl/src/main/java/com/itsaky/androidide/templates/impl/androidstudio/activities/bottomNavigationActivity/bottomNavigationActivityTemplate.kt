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

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.LAYOUT
import com.itsaky.androidide.templates.Constraint.NAVIGATION
import com.itsaky.androidide.templates.Constraint.NONEMPTY
import com.itsaky.androidide.templates.Constraint.UNIQUE
import com.itsaky.androidide.templates.FormFactor
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageNameWidget
import com.itsaky.androidide.templates.StringParameter
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.activityToLayout
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.itsaky.androidide.templates.layoutToActivity
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

val bottomNavigationActivityTemplate
  get() = template {
    name = "Bottom Navigation Views Activity"
    description = "Creates a new activity with bottom navigation"
    minApi = MIN_API
    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens =
        listOf(
            WizardUiContext.ActivityGallery,
            WizardUiContext.MenuEntry,
            WizardUiContext.NewProject,
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

    val packageName = defaultPackageNameParameter

    val navGraphName = stringParameter {
      name = "Navigation graph name"
      default = "mobile_navigation"
      help = "The name of the navigation graph"
      visible = { false }
      constraints = listOf(NAVIGATION, UNIQUE)
      suggest = { "mobile_navigation" }
      loggable = true
    }

    widgets(
        TextFieldWidget(activityClass),
        TextFieldWidget(layoutName),
        PackageNameWidget(packageName),
        LanguageWidget(),

        // Invisible widget. Defining this to impose constraints
        TextFieldWidget(navGraphName),
    )

    thumb { File("bottom-navigation-activity").resolve("template_bottom_navigation_activity.png") }

    recipe = { data: TemplateData ->
      bottomNavigationActivityRecipe(
          moduleData = data as ModuleTemplateData,
          activityClass = activityClass.value,
          layoutName = layoutName.value,
          packageName = packageName.value,
          navGraphName = navGraphName.value,
      )
    }
  }
