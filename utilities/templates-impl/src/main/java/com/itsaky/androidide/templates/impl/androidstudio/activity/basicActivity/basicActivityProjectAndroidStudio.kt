package com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun basicActivityProjectAndroidStudio(): ProjectTemplate = baseProjectImpl {
  defaultAppModule {
    recipe = createRecipe {
      // TODO: wire generateBasicActivity() with templates-api module data model.
    }
  }
}
