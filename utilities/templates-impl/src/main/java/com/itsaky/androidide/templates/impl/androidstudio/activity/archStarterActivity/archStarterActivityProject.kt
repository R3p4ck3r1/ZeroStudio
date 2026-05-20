package com.itsaky.androidide.templates.impl.androidstudio.activity.archStarterActivity

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun archStarterActivityProject(): ProjectTemplate = baseProjectImpl {
  defaultAppModule {
    recipe = createRecipe {
      // TODO: wire archStarterActivityRecipe to templates-api module data surface.
    }
  }
}
