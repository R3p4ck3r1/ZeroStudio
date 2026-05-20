package com.itsaky.androidide.templates.impl.androidstudio.activity.cppEmptyActivity

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun cppEmptyActivityProjectAndroidStudio(): ProjectTemplate = baseProjectImpl {
  defaultAppModule {
    recipe = createRecipe {
      // TODO: wire cppEmptyActivity recipe with templates-api module data.
    }
  }
}
