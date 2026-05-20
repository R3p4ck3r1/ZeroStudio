package com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter.src.app_package

import com.itsaky.androidide.templates.escapeKotlinIdentifier

fun mainActivityKt(activityClass: String, defaultPreview: String, greeting: String, packageName: String, themeName: String) = """
package ${escapeKotlinIdentifier(packageName)}

import androidx.compose.runtime.Composable
class $activityClass
@Composable fun ${greeting}(name: String){}
@Composable fun ${defaultPreview}(){}
"""
