package com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.SrcSet
import com.itsaky.androidide.templates.requireModuleData
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.addComposeDependencies

fun aiStarterActivityProject(): ProjectTemplate = baseProjectImpl {
  defaultAppModule {
    recipe = createRecipe {
      val data = requireModuleData()
      val srcOut = data.srcFolder(SrcSet.Main).resolve("java/${data.packageName.replace('.', '/')}").also { it.mkdirs() }
      addComposeDependencies(data)
      save(mainActivityKt("MainActivity", "GreetingPreview", "Greeting", data.packageName, "${data.name}Theme"), srcOut.resolve("MainActivity.kt"))
    }
  }
}
