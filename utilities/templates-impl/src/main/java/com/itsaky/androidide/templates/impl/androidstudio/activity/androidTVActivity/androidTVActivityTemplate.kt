package com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun androidTVActivityProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_androidtv
  thumb = R.drawable.template_leanback_tv
  description = string.title_template_description_androidtv

  defaultAppModule { recipe = androidTVActivityRecipe() }
}
