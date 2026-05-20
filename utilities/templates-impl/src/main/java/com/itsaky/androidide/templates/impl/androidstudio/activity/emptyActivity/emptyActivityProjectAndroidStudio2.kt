package com.itsaky.androidide.templates.impl.androidstudio.activity.emptyActivity

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun emptyActivityProjectAndroidStudio2(): ProjectTemplate = baseProjectImpl {
  defaultAppModule {
    recipe = createRecipe {
      // TODO: wire emptyActivity recipe with templates-api module data.
    }
  }
}
