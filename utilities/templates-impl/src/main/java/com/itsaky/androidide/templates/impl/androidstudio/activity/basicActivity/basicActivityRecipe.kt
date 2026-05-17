package com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.*
import com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity.res.layout.*
import com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity.res.navigation.navGraphXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity.res.values.stringsXml
import com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity.src.*
import com.itsaky.androidide.templates.impl.base.createRecipe

internal fun AndroidModuleTemplateBuilder.basicActivityRecipe() = createRecipe {
  sources {
    if (data.language == com.itsaky.androidide.templates.Language.Kotlin) {
      writeKtSrc(data.packageName, "MainActivity", source = ::basicActivityKt)
      writeKtSrc(data.packageName, "FirstFragment", source = ::firstFragmentKt)
      writeKtSrc(data.packageName, "SecondFragment", source = ::secondFragmentKt)
    } else {
      writeJavaSrc(data.packageName, "MainActivity", source = ::basicActivityJava)
      writeJavaSrc(data.packageName, "FirstFragment", source = ::firstFragmentJava)
      writeJavaSrc(data.packageName, "SecondFragment", source = ::secondFragmentJava)
    }
  }
  res {
    writeXmlResource("fragment_first", LAYOUT, source = ::fragmentFirstLayout)
    writeXmlResource("fragment_second", LAYOUT, source = ::fragmentSecondLayout)
    writeXmlResource("activity_main", LAYOUT, source = ::fragmentSimpleXml)
    writeXmlResource("nav_graph", NAVIGATION, source = ::navGraphXml)
    writeXmlResource("strings", VALUES, source = ::stringsXml)
  }
}


private fun AndroidModuleTemplateBuilder.basicActivityKt() = com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity.src.basicActivityKt(data.packageName)
private fun AndroidModuleTemplateBuilder.basicActivityJava() = com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity.src.basicActivityJava(data.packageName)
private fun AndroidModuleTemplateBuilder.firstFragmentKt() = com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity.src.firstFragmentKt(data.packageName)
private fun AndroidModuleTemplateBuilder.firstFragmentJava() = com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity.src.firstFragmentJava(data.packageName)
private fun AndroidModuleTemplateBuilder.secondFragmentKt() = com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity.src.secondFragmentKt(data.packageName)
private fun AndroidModuleTemplateBuilder.secondFragmentJava() = com.itsaky.androidide.templates.impl.androidstudio.activity.basicActivity.src.secondFragmentJava(data.packageName)
