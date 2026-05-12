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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.loginFragment

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.LAYOUT
import com.itsaky.androidide.templates.Constraint.NONEMPTY
import com.itsaky.androidide.templates.Constraint.UNIQUE
import com.itsaky.androidide.templates.FormFactor
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageNameWidget
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.fragmentToLayout
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import java.io.File

val loginFragmentTemplate
  get() = template {
    name = "Login Fragment"
    description =
        "Creates a new login fragment, allowing users to enter an email address and password to log in or to register with your application"
    minApi = MIN_API
    category = Category.Fragment
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.FragmentGallery, WizardUiContext.MenuEntry)

    val fragmentClass = stringParameter {
      name = "Fragment Name"
      default = "LoginFragment"
      help = "The name of the fragment class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val layoutName = stringParameter {
      name = "Layout Name"
      default = "fragment_login"
      help = "The name of the layout to create for the fragment"
      constraints = listOf(LAYOUT, UNIQUE, NONEMPTY)
      suggest = { fragmentToLayout(fragmentClass.value) }
      loggable = true
    }

    val packageName = defaultPackageNameParameter

    widgets(
        TextFieldWidget(fragmentClass),
        TextFieldWidget(layoutName),
        PackageNameWidget(packageName),
        LanguageWidget(),
    )

    thumb { File("login-fragment").resolve("template_login_fragment.png") }

    recipe = { data: TemplateData ->
      loginFragmentRecipe(
          data as ModuleTemplateData,
          fragmentClass.value,
          layoutName.value,
          packageName.value,
      )
    }
  }
