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

package com.itsaky.androidide.templates.impl.androidstudio.activities.loginActivity

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.LAYOUT
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
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

val loginActivityTemplate
  get() = template {
    name = "Login Views Activity"
    description =
        "Creates a new login activity, allowing users to enter an email address and password to log in or to register with your application"
    minApi = MIN_API
    category = Category.Activity
    formFactor = FormFactor.Mobile
    screens =
        listOf(
            WizardUiContext.ActivityGallery,
            WizardUiContext.MenuEntry,
            WizardUiContext.NewModule,
        )

    lateinit var layoutName: StringParameter
    val activityClass = stringParameter {
      name = "Activity Name"
      default = "LoginActivity"
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    layoutName = stringParameter {
      name = "Layout Name"
      default = "activity_login"
      help = "The name of the layout to create for the activity"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { activityToLayout(activityClass.value) }
      loggable = true
    }

    val packageName = defaultPackageNameParameter

    widgets(
        TextFieldWidget(activityClass),
        TextFieldWidget(layoutName),
        PackageNameWidget(packageName),
        LanguageWidget(),
    )

    thumb { File("login-activity").resolve("template_login_activity.png") }

    recipe = { data: TemplateData ->
      loginActivityRecipe(
          data as ModuleTemplateData,
          activityClass.value,
          layoutName.value,
          packageName.value,
      )
    }
  }
