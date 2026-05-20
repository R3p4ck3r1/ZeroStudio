package com.itsaky.androidide.templates.impl.androidstudio.activity.googleWalletActivity

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun googleWalletActivityProjectAndroidStudio(): ProjectTemplate = baseProjectImpl {
  defaultAppModule {
    recipe = createRecipe {
      // TODO: wire googleWalletActivity recipe with templates-api module data.
    }
  }
}
