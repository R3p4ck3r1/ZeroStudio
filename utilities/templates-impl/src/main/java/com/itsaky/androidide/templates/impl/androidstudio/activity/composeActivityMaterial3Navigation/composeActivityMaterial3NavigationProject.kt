package com.itsaky.androidide.templates.impl.androidstudio.activity.composeActivityMaterial3Navigation

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun composeActivityMaterial3NavigationProject(): ProjectTemplate = baseProjectImpl {
  defaultAppModule {
    recipe = createRecipe {
      // TODO: wire composeActivityMaterial3Navigation recipe with templates-api module data.
    }
  }
}
