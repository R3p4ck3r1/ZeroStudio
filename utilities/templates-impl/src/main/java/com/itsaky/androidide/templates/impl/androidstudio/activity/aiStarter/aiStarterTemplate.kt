package com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.Language.Kotlin
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.ProjectVersionData
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.projectLanguageParameter

private const val composeKotlinVersion = "2.2.0"

private fun aiStarterLanguageParameter() = projectLanguageParameter {
  default = Kotlin
  filter = { it == Kotlin }
}

fun aiStarterActivityProject(): ProjectTemplate =
    baseProjectImpl(
        language = aiStarterLanguageParameter(),
        projectVersionData = ProjectVersionData(kotlin = composeKotlinVersion),
    ) {
      templateName = R.string.template_ai_starter_activity
      thumb = R.drawable.template_compose_empty_activity_material3
      description = string.title_template_description_ai_starter_activity

      defaultAppModule(addAndroidX = false) {
        isComposeModule = true
        recipe = aiStarterRecipe()
      }
    }
