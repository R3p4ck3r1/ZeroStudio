package com.itsaky.androidide.templates.impl.androidstudio.activity.composeTvActivity

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun composeTvActivityProjectAndroidStudio(): ProjectTemplate = baseProjectImpl {
  defaultAppModule {
    recipe = createRecipe {
      // TODO: wire composeTvActivity recipe with templates-api module data.
    }
  }
}
