package com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity

internal fun androidManifestXml(
    activityClass: String,
    detailsActivity: String,
    isLibrary: Boolean,
    isNewModule: Boolean,
    packageName: String,
    themeName: String,
): String = """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application android:theme="$themeName">
    <activity android:name="$packageName.$activityClass" android:exported="${!isLibrary}" />
    <activity android:name="$packageName.$detailsActivity" android:exported="false" />
  </application>
</manifest>
""".trim()
