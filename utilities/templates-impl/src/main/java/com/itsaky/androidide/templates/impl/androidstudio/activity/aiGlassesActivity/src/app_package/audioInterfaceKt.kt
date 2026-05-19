package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package

import com.itsaky.androidide.templates.escapeKotlinIdentifier

fun audioInterfaceKt(packageName: String) = """
package ${escapeKotlinIdentifier(packageName)}
class AudioInterface
"""
