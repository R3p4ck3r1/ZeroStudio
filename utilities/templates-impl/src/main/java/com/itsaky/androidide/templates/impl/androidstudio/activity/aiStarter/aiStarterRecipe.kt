package com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.composeDependencies
import com.itsaky.androidide.templates.base.models.parseDependency
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
    addDependency(parseDependency("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7", tomlAlias = "androidx-lifecycle-viewmodel-compose"))
    addDependency(parseDependency("androidx.lifecycle:lifecycle-runtime-compose:2.8.7", tomlAlias = "androidx-lifecycle-runtime-compose"))
    addDependency(parseDependency("androidx.navigation:navigation-compose:2.8.9", tomlAlias = "androidx-navigation-compose"))
    addDependency(parseDependency("androidx.room:room-runtime:2.7.0", tomlAlias = "androidx-room-runtime"))
    addDependency(parseDependency("androidx.room:room-ktx:2.7.0", tomlAlias = "androidx-room-ktx"))
    addDependency(parseDependency("io.coil-kt:coil-compose:2.7.0", tomlAlias = "coil-compose"))
    addDependency(parseDependency("com.squareup.retrofit2:retrofit:2.12.0", tomlAlias = "retrofit"))
    addDependency(parseDependency("androidx.datastore:datastore-preferences:1.1.7", tomlAlias = "datastore-preferences"))
  }

  manifest { addPermission("android.permission.INTERNET") }
  sources {
    writeMainActivity(this, ktSrc = ::mainActivityKt, javaSrc = { "" })
    writeKtSrc("${data.packageName}.ui.theme", "Color", source = ::themeColorSrc)
    writeKtSrc("${data.packageName}.ui.theme", "Theme", source = ::themeThemeSrc)
    writeKtSrc("${data.packageName}.ui.theme", "Type", source = ::themeTypeSrc)
  }
  res { writeXmlResource("themes", com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.VALUES, source = ::srcThemesXml) }
}
