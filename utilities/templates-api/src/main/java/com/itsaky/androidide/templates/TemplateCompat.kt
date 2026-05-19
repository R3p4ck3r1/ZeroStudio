package com.itsaky.androidide.templates

fun renderIf(condition: Boolean, body: () -> String): String = if (condition) body() else ""

fun String.withoutSkipLines(): String =
    lineSequence().filter { it.isNotBlank() }.joinToString("\n")

fun underscoreToCamelCase(string: String): String =
    string.split('_').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar(Char::uppercase) }

fun underscoreToLowerCamelCase(string: String): String =
    underscoreToCamelCase(string).replaceFirstChar { it.lowercase() }

fun escapeKotlinIdentifier(identifier: String): String =
    identifier.split('.').joinToString(".") { part -> if (part in kotlinKeywords) "`${part}`" else part }

fun activityToLayout(activityName: String, layoutName: String? = null): String {
    if (layoutName != null) return layoutName
    return activityName
        .removeSuffix("Activity")
        .fold(StringBuilder()) { acc, c ->
          if (c.isUpperCase() && acc.isNotEmpty()) acc.append('_')
          acc.append(c.lowercaseChar())
        }.toString()
}

fun getMaterialComponentName(mavenCoordinate: String, useAndroidX: Boolean): String =
    if (useAndroidX) {
      mavenCoordinate
          .replace("android.support.v4", "androidx")
          .replace("android.support.v7", "androidx.appcompat")
          .replace("android.support.constraint", "androidx.constraintlayout")
          .replace("android.arch.lifecycle", "androidx.lifecycle")
          .replace("android.support.annotation", "androidx.annotation")
    } else mavenCoordinate

private val kotlinKeywords = setOf(
    "package","as","typealias","class","this","super","val","var","fun","for","null",
    "true","false","is","in","throw","return","break","continue","object","if","try",
    "else","while","do","when","interface","typeof"
)
