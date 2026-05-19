package com.itsaky.androidide.templates.impl.androidstudio.activity.bottomNavigationActivity

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun bottomNavigationActivityProjectAndroidStudio(): ProjectTemplate = baseProjectImpl {
  defaultAppModule {
    recipe = createRecipe {
      // TODO: wire bottomNavigationActivityRecipe() with templates-api module data.
    }
  }
}
