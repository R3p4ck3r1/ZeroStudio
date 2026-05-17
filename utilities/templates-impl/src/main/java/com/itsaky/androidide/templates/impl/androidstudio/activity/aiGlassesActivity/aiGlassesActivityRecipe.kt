package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity

import com.itsaky.androidide.templates.TemplateRecipe
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.composeDependencies
import com.itsaky.androidide.templates.base.models.Dependency
import com.itsaky.androidide.templates.base.models.parseDependency
import com.itsaky.androidide.templates.base.modules.android.ManifestActivity
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.VALUES
import com.itsaky.androidide.templates.base.util.SourceWriter
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.audioInterfaceKt
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.glassesActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.writeMainActivity

internal fun AndroidModuleTemplateBuilder.aiGlassesActivityRecipe() = createRecipe {
  composeDependencies()
  executor.apply {
    addDependency(Dependency.AndroidX.Compose.LifeCycle_Runtime_Ktx)
    addDependency(parseDependency("androidx.xr.glimmer:glimmer:1.0.0-alpha02"))
    addDependency(parseDependency("androidx.xr.projected:projected:1.0.0-alpha03"))
  }

  sources { writeAiGlassesSources(this) }
  res { writeXmlResource("strings", VALUES, source = ::stringsXml) }

  manifest {
    addActivity(ManifestActivity(name = "MainActivity", isExported = true, isLauncher = true))
    addActivity(
        ManifestActivity(
            name = "GlassesMainActivity",
            isExported = true,
            configureAttrs = { attribute("android:requiredDisplayCategory", "@string/display_category_xr_projected") },
        )
    )
  }
}

private fun AndroidModuleTemplateBuilder.writeAiGlassesSources(writer: SourceWriter) {
  writeMainActivity(writer, ktSrc = ::mainActivityKt, javaSrc = { "" })
  writeKtSrc(data.packageName, "GlassesMainActivity", source = ::glassesActivityKt)
  writeKtSrc(data.packageName, "AudioInterface", source = ::audioInterfaceKt)
}
