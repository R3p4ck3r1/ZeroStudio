package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package

import com.itsaky.androidide.templates.escapeKotlinIdentifier

fun mainActivityKt(activityClass: String, glassesActivityClass: String, packageName: String, themeName: String) = """
package ${escapeKotlinIdentifier(packageName)}
class $activityClass
"""
