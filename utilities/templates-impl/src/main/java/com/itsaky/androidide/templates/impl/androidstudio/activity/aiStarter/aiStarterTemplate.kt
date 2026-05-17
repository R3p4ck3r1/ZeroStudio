package com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun aiStarterActivityProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_ai_starter_activity
  thumb = R.drawable.template_compose_empty_activity_material3
  description = string.title_template_description_ai_starter_activity

  defaultAppModule {
    recipe = aiStarterRecipe()
  }
}
