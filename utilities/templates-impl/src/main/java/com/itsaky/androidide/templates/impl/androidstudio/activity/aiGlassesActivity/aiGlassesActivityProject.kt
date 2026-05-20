package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity

import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.SrcSet
import com.itsaky.androidide.templates.requireModuleData
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.audioInterfaceKt
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.glassesActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.mainActivityKt

fun aiGlassesActivityProject(): ProjectTemplate = baseProjectImpl {
  defaultAppModule {
    recipe = createRecipe {
      val data = requireModuleData()
      val srcOut = data.srcFolder(SrcSet.Main).resolve("java/${data.packageName.replace('.', '/')}").also { it.mkdirs() }
      val resOut = data.srcFolder(SrcSet.Main).resolve("res/values").also { it.mkdirs() }
      val glasses = "GlassesMainActivity"
      save(mainActivityKt("MainActivity", glasses, data.packageName, "${data.name}Theme"), srcOut.resolve("MainActivity.kt"))
      save(glassesActivityKt(glasses, data.packageName), srcOut.resolve("$glasses.kt"))
      save(audioInterfaceKt(data.packageName), srcOut.resolve("AudioInterface.kt"))
      save(stringsXml(), resOut.resolve("strings.xml"))
    }
  }
}
