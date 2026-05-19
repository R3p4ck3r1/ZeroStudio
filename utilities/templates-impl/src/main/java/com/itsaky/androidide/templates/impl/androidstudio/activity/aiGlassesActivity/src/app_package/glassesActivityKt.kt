package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package

import com.itsaky.androidide.templates.escapeKotlinIdentifier

fun glassesActivityKt(activityClass: String, packageName: String) = """
package ${escapeKotlinIdentifier(packageName)}
class $activityClass
"""
