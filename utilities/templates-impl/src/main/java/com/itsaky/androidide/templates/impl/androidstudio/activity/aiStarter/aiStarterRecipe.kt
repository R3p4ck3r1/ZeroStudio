package com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.models.Dependency
import com.itsaky.androidide.templates.base.models.parseDependency
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.LAYOUT
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.VALUES
import com.itsaky.androidide.templates.base.util.SourceWriter
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter.src.app_package.mainActivityJava
import com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter.src.app_package.mainActivityKt
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.emptyThemesAndColors
import com.itsaky.androidide.templates.impl.base.writeMainActivity

internal fun AndroidModuleTemplateBuilder.aiStarterRecipe() = createRecipe {
  executor.apply {
    addDependency(Dependency.AndroidX.Core_Ktx)
    addDependency(Dependency.AndroidX.AppCompat)
    addDependency(Dependency.Google.Material)
    addDependency(parseDependency("androidx.navigation:navigation-fragment-ktx:2.8.9", tomlAlias = "androidx-navigation-fragment-ktx"))
    addDependency(parseDependency("androidx.navigation:navigation-ui-ktx:2.8.9", tomlAlias = "androidx-navigation-ui-ktx"))
    addDependency(parseDependency("androidx.room:room-runtime:2.7.0", tomlAlias = "androidx-room-runtime"))
    addDependency(parseDependency("androidx.room:room-ktx:2.7.0", tomlAlias = "androidx-room-ktx"))
    addDependency(parseDependency("com.squareup.retrofit2:retrofit:2.12.0", tomlAlias = "retrofit"))
    addDependency(parseDependency("androidx.datastore:datastore-preferences:1.1.7", tomlAlias = "datastore-preferences"))
  }

  manifest { addPermission("android.permission.INTERNET") }
  sources { writeMainActivity(this, ::mainActivityKt, ::mainActivityJava) }
  res {
    writeXmlResource("activity_main", LAYOUT, source = ::activityMainXml)
    writeXmlResource("strings", VALUES, source = ::stringsXml)
    emptyThemesAndColors()
  }
}

private fun activityMainXml() = """
<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:layout_width=\"match_parent\"
    android:layout_height=\"match_parent\">

    <TextView
        android:id=\"@+id/message\"
        android:layout_width=\"wrap_content\"
        android:layout_height=\"wrap_content\"
        android:layout_gravity=\"center\"
        android:text=\"@string/ai_starter_message\" />
</FrameLayout>
""".trim()

private fun stringsXml() = """
<resources>
    <string name=\"app_name\">AI Starter Activity</string>
    <string name=\"ai_starter_message\">AI Starter ready.</string>
</resources>
""".trim()
