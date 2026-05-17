package com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.models.parseDependency
import com.itsaky.androidide.templates.base.modules.android.ManifestActivity
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.LAYOUT
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.VALUES
import com.itsaky.androidide.templates.base.util.SourceWriter
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.layout.activityDetailsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.layout.activityMainXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.values.colorsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.values.themesXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.src.app_package.detailsActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.src.app_package.detailsActivityKt
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.src.app_package.mainActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.writeMainActivity

internal fun AndroidModuleTemplateBuilder.androidTVActivityRecipe() = createRecipe {
  executor.apply {
    addDependency(parseDependency("androidx.leanback:leanback:1.2.0", tomlAlias = "androidx-leanback"))
    addDependency(parseDependency("com.github.bumptech.glide:glide:4.11.0", tomlAlias = "glide"))
  }

  manifest {
    addActivity(ManifestActivity(name = "MainActivity", isExported = true, isLauncher = true))
    addActivity(ManifestActivity(name = "DetailsActivity", isExported = false))
  }

  sources { writeTvSources(this) }
  res {
    writeXmlResource("activity_main", LAYOUT, source = ::activityMainXml)
    writeXmlResource("activity_details", LAYOUT, source = ::activityDetailsXml)
    writeXmlResource("strings", VALUES, source = ::stringsXml)
    writeXmlResource("colors", VALUES, source = ::colorsXml)
    writeXmlResource("themes", VALUES, source = ::themesXml)
  }
}

private fun AndroidModuleTemplateBuilder.writeTvSources(writer: SourceWriter) {
  writeMainActivity(writer, ktSrc = ::mainActivityKt, javaSrc = ::mainActivityJava)
  if (data.language == com.itsaky.androidide.templates.Language.Kotlin) {
    writeKtSrc(data.packageName, "DetailsActivity", source = ::detailsActivityKt)
  } else {
    writeJavaSrc(data.packageName, "DetailsActivity", source = ::detailsActivityJava)
  }
}
