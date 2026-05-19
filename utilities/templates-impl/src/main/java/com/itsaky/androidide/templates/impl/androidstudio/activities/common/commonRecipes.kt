package com.itsaky.androidide.templates.impl.androidstudio.activities.common

import com.itsaky.androidide.templates.ModuleTemplateData
import com.itsaky.androidide.templates.RecipeExecutor
import com.itsaky.androidide.templates.base.models.Dependency
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.res.layout.simpleLayoutXml
import com.itsaky.androidide.templates.impl.androidstudio.activities.common.res.menu.simpleMenu
import java.io.File

private fun File.ensureParent() = parentFile?.mkdirs()

private fun RecipeExecutor.mergeXml(xml: String, out: File) {
  out.ensureParent()
  if (!out.exists()) return save(xml, out)
  val merged = out.readText().replace("</resources>", "\n${xml.trim()}\n</resources>")
  save(merged, out)
}

fun RecipeExecutor.generateSimpleMenu(resDir: File, menuName: String) {
  save(simpleMenu(), resDir.resolve("menu/$menuName.xml"))
  mergeXml("<string name=\"action_settings\">Settings</string>", resDir.resolve("values/strings.xml"))
}

fun RecipeExecutor.generateManifest(moduleData: ModuleTemplateData, activityClass: String, packageName: String) {
  save(androidManifestXml(packageName, activityClass, moduleData.name), moduleData.projectDir.resolve("src/main/AndroidManifest.xml"))
}

fun RecipeExecutor.generateSimpleLayout(moduleData: ModuleTemplateData, activityClass: String, layoutName: String) {
  val out = moduleData.projectDir.resolve("src/main/res/layout/$layoutName.xml")
  save(simpleLayoutXml(packageName = moduleData.packageName, activityClass = activityClass), out)
}

fun MutableList<Dependency>.addCommonAndroidX() {
  add(Dependency.AndroidX.AppCompat)
  add(Dependency.AndroidX.ConstraintLayout)
  add(Dependency.Google.Material)
}
