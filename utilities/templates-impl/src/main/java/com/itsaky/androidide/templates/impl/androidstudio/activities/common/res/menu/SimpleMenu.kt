package com.itsaky.androidide.templates.impl.androidstudio.activities.common.res.menu

fun simpleMenu(): String =
    """
    <menu xmlns:android=\"http://schemas.android.com/apk/res/android\">
      <item android:id=\"@+id/action_settings\" android:title=\"@string/action_settings\"/>
    </menu>
    """.trimIndent()
