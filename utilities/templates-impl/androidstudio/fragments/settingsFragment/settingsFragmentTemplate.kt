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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.settingsFragment

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.Constraint.*
import com.itsaky.androidide.templates.FormFactor
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageNameWidget
import com.itsaky.androidide.templates.TemplateConstraint
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import java.io.File

val settingsFragmentTemplate
  get() = template {
    name = "Settings Fragment"
    description = "Creates a new fragment that allows a user to configure application settings"
    minApi = MIN_API
    constraints = listOf(TemplateConstraint.AndroidX)

    category = Category.Fragment
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.FragmentGallery, WizardUiContext.MenuEntry)

    val fragmentClass = stringParameter {
      name = "Fragment Name"
      default = "SettingsFragment"
      help = "The name of the fragment class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
      loggable = true
    }

    val packageName = defaultPackageNameParameter

    widgets(TextFieldWidget(fragmentClass), PackageNameWidget(packageName), LanguageWidget())

    thumb { File("settings-fragment").resolve("template_settings_fragment.png") }

    recipe = { data: TemplateData ->
      settingsFragmentRecipe(data as ModuleTemplateData, fragmentClass.value, packageName.value)
    }
  }
