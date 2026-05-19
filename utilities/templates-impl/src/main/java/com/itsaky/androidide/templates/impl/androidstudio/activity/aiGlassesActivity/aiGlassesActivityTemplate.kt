package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.Language.Kotlin
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.ProjectVersionData
import com.itsaky.androidide.templates.Sdk
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.minSdkParameter
import com.itsaky.androidide.templates.projectLanguageParameter

private const val composeKotlinVersion = "2.2.0"
private fun aiGlassesLanguageParameter() = projectLanguageParameter { default = Kotlin; filter = { it == Kotlin } }
private fun aiGlassesMinSdkParameter() = minSdkParameter { default = Sdk.Marshmallow }

fun aiGlassesActivityProject(): ProjectTemplate = baseProjectImpl(
    language = aiGlassesLanguageParameter(),
    minSdk = aiGlassesMinSdkParameter(),
    projectVersionData = ProjectVersionData(kotlin = composeKotlinVersion)
) {
  templateName = R.string.template_ai_glasses_activity
  thumb = R.drawable.template_ai_glasses_activity
  description = string.title_template_description_ai_glasses_activity
  defaultAppModule(addAndroidX = false) { isComposeModule = true; recipe = aiGlassesActivityRecipe() }
}
