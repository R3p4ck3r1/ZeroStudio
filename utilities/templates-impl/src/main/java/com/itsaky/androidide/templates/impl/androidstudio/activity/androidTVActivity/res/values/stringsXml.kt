package com.itsaky.androidide.templates.impl.androidstudio.activity.androidTVActivity.res.values

internal fun stringsXml(activityClass: String, isNewModule: Boolean) = """
<resources>
  <string name="app_name">Android TV Activity</string>
  <string name="title_${activityClass.lower()}">Android TV Activity</string>
</resources>
""".trim()
