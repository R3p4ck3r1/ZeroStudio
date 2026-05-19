package com.itsaky.androidide.templates.impl.androidstudio.activities.common

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor

fun RecipeExecutor.addDependency(mavenCoordinate: String, configuration: String = "implementation", minRev: String? = null) {
  // templates-api currently manages dependencies via template builders.
  // Kept as compatibility shim for migrated wizard macros.
}

fun RecipeExecutor.addPlatformDependency(mavenCoordinate: String, configuration: String = "implementation") {
}

fun RecipeExecutor.addPlugin(pluginId: String, mavenCoordinate: String, revision: String) {
}

fun RecipeExecutor.addLifecycleDependencies(useAndroidX: Boolean) {
  if (useAndroidX) {
    addDependency("androidx.lifecycle:lifecycle-livedata-ktx:+")
    addDependency("androidx.lifecycle:lifecycle-viewmodel-ktx:+")
  } else {
    addDependency("android.arch.lifecycle:extensions:+")
  }
}

val ModuleTemplateData.projectTemplateData get() = null
val ModuleTemplateData.isNewModule get() = true
