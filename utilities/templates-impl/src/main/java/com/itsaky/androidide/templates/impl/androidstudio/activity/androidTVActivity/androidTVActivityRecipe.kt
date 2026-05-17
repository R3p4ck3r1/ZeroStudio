package com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity

import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.models.parseDependency
import com.itsaky.androidide.templates.base.modules.android.ManifestActivity
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.LAYOUT
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.VALUES
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.layout.activityDetailsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.layout.activityMainXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.values.colorsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.values.themesXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.src.app_package.*
import com.itsaky.androidide.templates.impl.base.createRecipe

internal fun AndroidModuleTemplateBuilder.androidTVActivityRecipe() = createRecipe {
  executor.apply {
    addDependency(parseDependency("androidx.leanback:leanback:1.2.0", tomlAlias = "androidx-leanback"))
    addDependency(parseDependency("com.github.bumptech.glide:glide:4.11.0", tomlAlias = "glide"))
  }

  manifest {
    addActivity(ManifestActivity(name = "MainActivity", isExported = true, isLauncher = true))
    addActivity(ManifestActivity(name = "DetailsActivity", isExported = false))
  }

  res {
    writeXmlResource("activity_main", LAYOUT) { activityMainXml("MainActivity", data.packageName) }
    writeXmlResource("activity_details", LAYOUT) { activityDetailsXml("DetailsActivity", data.packageName) }
    writeXmlResource("strings", VALUES) { stringsXml("MainActivity", true) }
    writeXmlResource("colors", VALUES, source = ::colorsXml)
    writeXmlResource("themes", VALUES) { themesXml("Theme.MyApplication") }
  }

  sources {
    if (data.language == Language.Kotlin) {
      writeKtSrc(data.packageName, "MainActivity") { mainActivityKt("MainActivity", "activity_main", "MainFragment", data.packageName) }
      writeKtSrc(data.packageName, "MainFragment") { mainFragmentKt("DetailsActivity", "MainFragment", 21, data.packageName) }
      writeKtSrc(data.packageName, "DetailsActivity") { detailsActivityKt("DetailsActivity", "VideoDetailsFragment", "activity_details", data.packageName) }
      writeKtSrc(data.packageName, "VideoDetailsFragment") { videoDetailsFragmentKt("MainActivity","DetailsActivity","VideoDetailsFragment",21,data.packageName) }
      writeKtSrc(data.packageName, "Movie") { movieKt(data.packageName) }
      writeKtSrc(data.packageName, "MovieList") { movieListKt(data.packageName) }
      writeKtSrc(data.packageName, "CardPresenter") { cardPresenterKt(data.packageName) }
      writeKtSrc(data.packageName, "DetailsDescriptionPresenter") { detailsDescriptionPresenterKt(data.packageName) }
      writeKtSrc(data.packageName, "PlaybackActivity") { playbackActivityKt(data.packageName) }
      writeKtSrc(data.packageName, "PlaybackVideoFragment") { playbackVideoFragmentKt(21, data.packageName) }
      writeKtSrc(data.packageName, "BrowseErrorActivity") { browseErrorActivityKt("activity_main", data.packageName, "MainFragment") }
      writeKtSrc(data.packageName, "ErrorFragment") { errorFragmentKt(21, data.packageName) }
    } else {
      writeJavaSrc(data.packageName, "MainActivity") { mainActivityJava("MainActivity", "activity_main", "MainFragment", data.packageName) }
      writeJavaSrc(data.packageName, "MainFragment") { mainFragmentJava("DetailsActivity", "MainFragment", 21, data.packageName) }
      writeJavaSrc(data.packageName, "DetailsActivity") { detailsActivityJava("DetailsActivity", "VideoDetailsFragment", "activity_details", data.packageName) }
      writeJavaSrc(data.packageName, "VideoDetailsFragment") { videoDetailsFragmentJava("MainActivity","DetailsActivity","VideoDetailsFragment",21,data.packageName) }
      writeJavaSrc(data.packageName, "Movie") { movieJava(data.packageName) }
      writeJavaSrc(data.packageName, "MovieList") { movieListJava(data.packageName) }
      writeJavaSrc(data.packageName, "CardPresenter") { cardPresenterJava(data.packageName) }
      writeJavaSrc(data.packageName, "DetailsDescriptionPresenter") { detailsDescriptionPresenterJava(data.packageName) }
      writeJavaSrc(data.packageName, "PlaybackActivity") { playbackActivityJava(data.packageName) }
      writeJavaSrc(data.packageName, "PlaybackVideoFragment") { playbackVideoFragmentJava(21, data.packageName) }
      writeJavaSrc(data.packageName, "BrowseErrorActivity") { browseErrorActivityJava("activity_main", data.packageName, "MainFragment") }
      writeJavaSrc(data.packageName, "ErrorFragment") { errorFragmentJava(21, data.packageName) }
    }
  }
}
