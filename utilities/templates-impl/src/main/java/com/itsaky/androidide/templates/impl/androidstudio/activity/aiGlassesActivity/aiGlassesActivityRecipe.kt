package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.models.Dependency
import com.itsaky.androidide.templates.base.models.parseDependency
import com.itsaky.androidide.templates.base.modules.android.ManifestActivity
import com.itsaky.androidide.templates.base.util.SourceWriter
import com.itsaky.androidide.templates.base.util.SourceWriter.writeJavaSrc
import com.itsaky.androidide.templates.base.util.SourceWriter.writeKtSrc
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.audioInterfaceJava
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.audioInterfaceKt
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.glassesActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.glassesActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.mainActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.writeMainActivity

internal fun AndroidModuleTemplateBuilder.aiGlassesActivityRecipe() = createRecipe {
  executor.apply {
    addDependency(Dependency.AndroidX.Core_Ktx)
    addDependency(Dependency.AndroidX.AppCompat)
    addDependency(Dependency.Google.Material)
    addDependency(parseDependency("androidx.xr.projected:projected:1.0.0-alpha03", tomlAlias = "androidx-xr-projected"))
  }

  sources { writeAiGlassesSources(this) }
  res { writeXmlResource("strings", com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.VALUES, source = ::stringsXml) }

  manifest {
    addActivity(ManifestActivity(name = "MainActivity", isExported = true, isLauncher = true))
    addActivity(
        ManifestActivity(
            name = "GlassesMainActivity",
            isExported = true,
        ))
  }
}

private fun AndroidModuleTemplateBuilder.writeAiGlassesSources(writer: SourceWriter) {
  writeMainActivity(writer, ktSrc = ::mainActivityKt, javaSrc = ::mainActivityJava)
  writer.apply {
    if (data.language == com.itsaky.androidide.templates.Language.Kotlin) {
      writeKtSrc(data.packageName, "GlassesMainActivity", source = ::glassesActivityKt)
      writeKtSrc(data.packageName, "AudioInterface", source = ::audioInterfaceKt)
    } else {
      writeJavaSrc(data.packageName, "GlassesMainActivity", source = ::glassesActivityJava)
      writeJavaSrc(data.packageName, "AudioInterface", source = ::audioInterfaceJava)
    }
  }
}
