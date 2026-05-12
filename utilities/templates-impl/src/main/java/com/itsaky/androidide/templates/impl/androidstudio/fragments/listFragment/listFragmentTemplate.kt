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

package com.itsaky.androidide.templates.impl.androidstudio.fragments.listFragment

import com.itsaky.androidide.templates.Category
import com.itsaky.androidide.templates.Constraint.CLASS
import com.itsaky.androidide.templates.Constraint.LAYOUT
import com.itsaky.androidide.templates.Constraint.NONEMPTY
import com.itsaky.androidide.templates.Constraint.UNIQUE
import com.itsaky.androidide.templates.EnumWidget
import com.itsaky.androidide.templates.FormFactor
import com.itsaky.androidide.templates.LanguageWidget
import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.PackageNameWidget
import com.itsaky.androidide.templates.TemplateData
import com.itsaky.androidide.templates.TextFieldWidget
import com.itsaky.androidide.templates.WizardUiContext
import com.itsaky.androidide.templates.enumParameter
import com.itsaky.androidide.templates.extractLetters
import com.itsaky.androidide.templates.fragmentToLayout
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.MIN_API
import com.itsaky.androidide.templates.impl.androidstudio.defaultPackageNameParameter
import com.itsaky.androidide.templates.stringParameter
import com.itsaky.androidide.templates.template
import java.io.File

@Suppress("EnumEntryName", "Unused")
enum class ColumnCount {
  `1 (List)`,
  `2 (Grid)`,
  `3`,
  `4`,
}

val listFragmentTemplate
  get() = template {
    name = "Fragment (List)"
    description =
        "Creates a new empty fragment containing a list that can be rendered as a grid. Compatible back to API level $MIN_API"
    minApi = MIN_API
    category = Category.Fragment
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.FragmentGallery, WizardUiContext.MenuEntry)

    val packageName = defaultPackageNameParameter
    val objectKind = stringParameter {
      name = "Object Kind"
      default = "Item"
      help = "Other examples are Person, Book, etc."
      constraints = listOf(NONEMPTY)
      loggable = true
    }

    val fragmentClass = stringParameter {
      name = "Fragment class name"
      default = "ItemFragment"
      constraints = listOf(NONEMPTY, CLASS, UNIQUE)
      suggest = { "${extractLetters(objectKind.value)}Fragment" }
      loggable = true
    }

    val columnCount =
        enumParameter<ColumnCount> {
          name = "Column Count"
          default = ColumnCount.`1 (List)`
          help = "The number of columns in the grid"
        }

    val fragmentLayout = stringParameter {
      name = "Object content layout file name"
      default = "fragment_item"
      constraints = listOf(LAYOUT, NONEMPTY, UNIQUE)
      suggest = { fragmentToLayout(fragmentClass.value) }
      loggable = true
    }

    val fragmentLayoutList = stringParameter {
      name = "List layout file name"
      default = "fragment_item_list"
      constraints = listOf(LAYOUT, NONEMPTY, UNIQUE)
      suggest = { "${fragmentToLayout(fragmentClass.value)}_list" }
      loggable = true
    }

    val adapterClassName = stringParameter {
      name = "Adapter class name"
      default = "MyItemRecyclerViewAdapter"
      constraints = listOf(NONEMPTY, CLASS, UNIQUE)
      suggest = { "My${extractLetters(objectKind.value)}RecyclerViewAdapter" }
      loggable = true
    }

    widgets(
        PackageNameWidget(packageName),
        TextFieldWidget(objectKind),
        TextFieldWidget(fragmentClass),
        EnumWidget(columnCount),
        TextFieldWidget(fragmentLayout),
        TextFieldWidget(fragmentLayoutList),
        TextFieldWidget(adapterClassName),
        LanguageWidget(),
    )

    thumb { File("list-fragment").resolve("template_list_fragment.png") }

    recipe = { data: TemplateData ->
      listFragmentRecipe(
          data as ModuleTemplateData,
          packageName.value,
          fragmentClass.value,
          columnCount.value,
          fragmentLayout.value,
          fragmentLayoutList.value,
          adapterClassName.value,
      )
    }
  }
