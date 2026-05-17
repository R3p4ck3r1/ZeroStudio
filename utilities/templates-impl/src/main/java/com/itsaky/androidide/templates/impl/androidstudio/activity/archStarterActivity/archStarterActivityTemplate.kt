package com.itsaky.androidide.templates.impl.androidstudio.activity.archStarterActivity

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.Language.Kotlin
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.projectLanguageParameter

private fun lang() = projectLanguageParameter { default = Kotlin; filter = { it==Kotlin } }
fun archStarterActivityProject(): ProjectTemplate = baseProjectImpl(language = lang()) {
  templateName = R.string.template_arch_starter_activity
  thumb = R.drawable.template_compose_empty_activity_material3
  description = string.title_template_description_arch_starter_activity
  defaultAppModule(addAndroidX = false) { isComposeModule = true; recipe = archStarterActivityRecipe() }
}
