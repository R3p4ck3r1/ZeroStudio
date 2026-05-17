package com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.composeDependencies
import com.itsaky.androidide.templates.base.models.parseDependency
import com.itsaky.androidide.templates.base.modules.android.ManifestActivity
import com.itsaky.androidide.templates.base.util.SourceWriter
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.writeMainActivity
import com.itsaky.androidide.templates.impl.composeActivity.srcThemesXml
import com.itsaky.androidide.templates.impl.composeActivity.themeColorSrc
import com.itsaky.androidide.templates.impl.composeActivity.themeThemeSrc
import com.itsaky.androidide.templates.impl.composeActivity.themeTypeSrc

internal fun AndroidModuleTemplateBuilder.aiStarterRecipe() = createRecipe {
  composeDependencies()
  executor.apply {
    addDependency(parseDependency("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7"))
    addDependency(parseDependency("androidx.lifecycle:lifecycle-runtime-compose:2.8.7"))
    addDependency(parseDependency("androidx.navigation:navigation-compose:2.8.9"))
    addDependency(parseDependency("androidx.room:room-runtime:2.7.0"))
    addDependency(parseDependency("androidx.room:room-ktx:2.7.0"))
    addDependency(parseDependency("androidx.room:room-compiler:2.7.0"))
    addDependency(parseDependency("io.coil-kt:coil-compose:2.7.0"))
    addDependency(parseDependency("com.squareup.retrofit2:retrofit:2.12.0"))
    addDependency(parseDependency("androidx.datastore:datastore-preferences:1.1.7"))
  }

  manifest {
    addPermission("android.permission.INTERNET")
    addActivity(ManifestActivity(name = "MainActivity", isExported = true, isLauncher = true))
  }

  sources { writeAiStarterSources(this) }
  res { writeXmlResource("themes", com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.VALUES, source = ::srcThemesXml) }
}

private fun AndroidModuleTemplateBuilder.writeAiStarterSources(writer: SourceWriter) {
  writeMainActivity(writer, ktSrc = ::mainActivityKt, javaSrc = { "" })
  writeKtSrc("${data.packageName}.ui.theme", "Color", source = ::themeColorSrc)
  writeKtSrc("${data.packageName}.ui.theme", "Theme", source = ::themeThemeSrc)
  writeKtSrc("${data.packageName}.ui.theme", "Type", source = ::themeTypeSrc)
}
