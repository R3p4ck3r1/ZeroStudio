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
package com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity

import com.itsaky.androidide.templates.BooleanParameter
import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.LAYOUT
import com.itsaky.androidide.templates.Constraint.NAVIGATION
import com.itsaky.androidide.templates.Constraint.NONEMPTY
import com.itsaky.androidide.templates.Constraint.UNIQUE
import com.itsaky.androidide.templates.FormFactor
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageNameWidget
import com.itsaky.androidide.templates.Separator
import com.itsaky.androidide.templates.StringParameter
import com.itsaky.androidide.templates.TemplateConstraint
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.impl.sharedMacros.activityToLayout
import com.itsaky.androidide.templates.booleanParameter
import com.itsaky.androidide.templates.classToResource
import com.itsaky.androidide.templates.impl.androidstudio.activity.common.MIN_API
import com.itsaky.androidide.templates.impl.defaultPackageNameParameter
import com.itsaky.androidide.templates.layoutToActivity
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

val basicActivityTemplate
  get() = template {
    name = "Basic Views Activity"
    minApi = MIN_API
    description = "Creates a new basic activity"

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

    lateinit var activityClass: StringParameter
    val layoutName: StringParameter = stringParameter {
      name = "Layout Name"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
      default = "activity_main"
      help = "The name of the layout to create for the activity"
      loggable = true
    }

    activityClass = stringParameter {
      name = "Activity Name"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { layoutToActivity(layoutName.value) }
      default = "MainActivity"
      help = "The name of the activity class to create"
      loggable = true
    }

    val menuName: StringParameter = stringParameter {
      name = "Menu Resource File"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { "menu_" + classToResource(activityClass.value) }
      visible = { isNewModule }
      default = "menu_main"
      help = "The name of the resource file to create for the menu items"
      loggable = true
    }
    val isLauncher: BooleanParameter = booleanParameter {
      name = "Launcher Activity"
      visible = { !isNewModule }
      default = false
      help =
          "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
    }

    val contentLayoutName: StringParameter = stringParameter {
      name = "Content Layout Name"
      constraints = listOf(LAYOUT, UNIQUE)
      suggest = { activityToLayout(activityClass.value, "content") }
      default = "content_main"
      visible = { false }
      help = "The name of the App Bar layout to create for the activity"
      loggable = true
    }

    val firstFragmentLayoutName: StringParameter = stringParameter {
      name = "First fragment Layout Name"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      default = "fragment_first"
      visible = { false }
      help = "The name of the layout of the Fragment as the initial destination in Navigation"
      loggable = true
    }

    val secondFragmentLayoutName: StringParameter = stringParameter {
      name = "First fragment Layout Name"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      default = "fragment_second"
      visible = { false }
      help = "The name of the layout of the Fragment as the second destination in Navigation"
      loggable = true
    }

    val navGraphName = stringParameter {
      name = "Navigation graph name"
      default = "nav_graph"
      help = "The name of the navigation graph"
      visible = { false }
      constraints = listOf(NAVIGATION, UNIQUE)
      suggest = { "nav_graph" }
      loggable = true
    }

    val packageName = defaultPackageNameParameter

    widgets(
        TextFieldWidget(activityClass),
        TextFieldWidget(layoutName),
        TextFieldWidget(menuName),
        CheckBoxWidget(isLauncher),
        Separator, // for example
        PackageNameWidget(packageName),
        LanguageWidget(),

        // Invisible widgets. Defining these to impose constraints
        TextFieldWidget(contentLayoutName),
        TextFieldWidget(firstFragmentLayoutName),
        TextFieldWidget(secondFragmentLayoutName),
        TextFieldWidget(navGraphName),
    )

    thumb { File("basic-activity-material3").resolve("template_basic_activity_material3.png") }

    recipe = { data: TemplateData ->
      val moduleData = data as ModuleTemplateData

      generateBasicActivity(
          moduleData = data,
          activityClass = activityClass.value,
          layoutName = layoutName.value,
          contentLayoutName = contentLayoutName.value,
          packageName = packageName.value,
          menuName = menuName.value,
          isLauncher = isLauncher.value,
          firstFragmentLayoutName = firstFragmentLayoutName.value,
          secondFragmentLayoutName = secondFragmentLayoutName.value,
          navGraphName = navGraphName.value,
      )
    }
  }
