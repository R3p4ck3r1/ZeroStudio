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
package com.itsaky.androidide.templates.impl.androidstudio.activities.cppEmptyActivity

import com.itsaky.androidide.templates.BooleanParameter
import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.CheckBoxWidget
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.LAYOUT
import com.itsaky.androidide.templates.Constraint.NONEMPTY
import com.itsaky.androidide.templates.Constraint.UNIQUE
import com.itsaky.androidide.templates.CppStandardType
import com.itsaky.androidide.templates.EnumWidget
import com.itsaky.androidide.templates.FormFactor
import com.itsaky.androidide.templates.LabelWidget
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageNameWidget
import com.itsaky.androidide.templates.StringParameter
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.UrlLinkWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.activityToLayout
import com.itsaky.androidide.templates.booleanParameter
import com.itsaky.androidide.templates.enumParameter
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.itsaky.androidide.templates.layoutToActivity
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

const val DOCUMENTATION_URL = "https://developer.android.com/ndk/guides/cpp-support.html"

val cppEmptyActivityTemplate
  get() = template {
    name = "Native C++"
    minApi = MIN_API
    description = "Creates a new project with an Empty Activity configured to use JNI"
    documentationUrl = DOCUMENTATION_URL

    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.NewProject, WizardUiContext.NewProjectExtraDetail)

    lateinit var layoutName: StringParameter
    val activityClass: StringParameter = stringParameter {
      name = "Activity Name"
      visible = { !isNewModule }
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      suggest = { layoutToActivity(layoutName.value) }
      default = "MainActivity"
      help = "The name of the activity class to create"
      loggable = true
    }
    layoutName = stringParameter {
      name = "Layout Name"
      visible = { !isNewModule }
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
      default = "activity_main"
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
    val cppStandard =
        enumParameter<CppStandardType> {
          name = "C++ Standard"
          default = CppStandardType.`Toolchain Default`
          help = "C++ Standard version"
        }
    val packageName = defaultPackageNameParameter

    widgets(
        TextFieldWidget(activityClass),
        TextFieldWidget(layoutName),
        CheckBoxWidget(isLauncher),
        PackageNameWidget(packageName),
        LanguageWidget(),
        EnumWidget(cppStandard),
        LabelWidget("C++ feature support depends on Android NDK version."),
        UrlLinkWidget("See documentation", DOCUMENTATION_URL),
    )

    thumb { File("cpp-empty-activity").resolve("cpp_configure.png") }

    recipe = { data ->
      generateCppEmptyActivity(
          data as ModuleTemplateData,
          activityClass.value,
          layoutName.value,
          isLauncher.value,
          packageName.value,
          cppStandard.value.toString(),
      )
    }
  }
