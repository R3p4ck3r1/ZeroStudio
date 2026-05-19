package com.itsaky.androidide.templates.impl.androidstudio.activities.common

fun androidManifestXml(packageName: String, activityClass: String, appLabel: String): String =
    """
    <manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$packageName\">
      <application android:label=\"$appLabel\">
        <activity android:name=\".$activityClass\" android:exported=\"true\">
          <intent-filter>
            <action android:name=\"android.intent.action.MAIN\" />
            <category android:name=\"android.intent.category.LAUNCHER\" />
          </intent-filter>
        </activity>
      </application>
    </manifest>
    """.trimIndent()
