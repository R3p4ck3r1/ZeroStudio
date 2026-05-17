package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun aiGlassesActivityProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_ai_glasses_activity
  thumb = R.drawable.template_ai_glasses_activity
  description = string.title_template_description_ai_glasses_activity

  defaultAppModule {
    recipe = aiGlassesActivityRecipe()
  }
}
