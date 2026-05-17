package com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun basicActivityProjectAndroidStudio(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_basic
  thumb = R.drawable.template_basic_activity
  description = string.title_test
  defaultAppModule { recipe = basicActivityRecipe() }
}
