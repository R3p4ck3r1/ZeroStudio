package com.itsaky.androidide.templates.impl.androidstudio.activities.common.res.layout

fun simpleLayoutXml(packageName: String, activityClass: String): String =
    """
    <androidx.constraintlayout.widget.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"
      xmlns:tools=\"http://schemas.android.com/tools\" android:layout_width=\"match_parent\"
      android:layout_height=\"match_parent\" tools:context=\"$packageName.$activityClass\"/>
    """.trimIndent()
