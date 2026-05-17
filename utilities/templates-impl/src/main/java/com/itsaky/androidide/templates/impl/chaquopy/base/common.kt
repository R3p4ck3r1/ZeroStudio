package com.itsaky.androidide.templates.impl.chaquopy.base

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import java.io.File

private const val AGP_VERSION = "8.13.0"
private const val KOTLIN_VERSION = "2.2.20"
private const val CHAQUOPY_VERSION = "17.0.0"
private const val COMPILE_SDK = 36
private const val MIN_SDK = 24
private const val TARGET_SDK = 36

internal fun AndroidModuleTemplateBuilder.writeChaquopyCommonFiles(
    appDependencies: String,
    extraKts: String = "",
    extraGroovy: String = "",
) {
  val appDir = File(data.projectDir, "app")
  val mainDir = File(appDir, "src/main")

  executor.save(androidManifest(), File(mainDir, "AndroidManifest.xml"))
  executor.save(pythonMain(), File(mainDir, "python/main.py"))

  executor.save(rootGradle(data.useKts), File(data.projectDir, if (data.useKts) "build.gradle.kts" else "build.gradle"))
  executor.save(appGradle(data.useKts, data.packageName, appDependencies, extraKts, extraGroovy), File(appDir, if (data.useKts) "build.gradle.kts" else "build.gradle"))

  if (data.useToml) {
    executor.save(versionCatalog(), File(data.projectDir, "gradle/libs.versions.toml"))
  }
}

private fun rootGradle(kts: Boolean) =
    if (kts) {
      """
plugins {
  id("com.android.application") version "$AGP_VERSION" apply false
  id("org.jetbrains.kotlin.android") version "$KOTLIN_VERSION" apply false
  id("com.chaquo.python") version "$CHAQUOPY_VERSION" apply false
}
""".trim()
    } else {
      """
plugins {
  id 'com.android.application' version '$AGP_VERSION' apply false
  id 'org.jetbrains.kotlin.android' version '$KOTLIN_VERSION' apply false
  id 'com.chaquo.python' version '$CHAQUOPY_VERSION' apply false
}
""".trim()
    }

private fun appGradle(
    kts: Boolean,
    pkg: String,
    deps: String,
    extraKts: String,
    extraGroovy: String,
) =
    if (kts) {
      """
plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.chaquo.python")
}

android {
  namespace = "$pkg"
  compileSdk = $COMPILE_SDK

  defaultConfig {
    applicationId = "$pkg"
    minSdk = $MIN_SDK
    targetSdk = $TARGET_SDK
    versionCode = 1
    versionName = "1.0"
  }
}

chaquopy {
  defaultConfig {
    pip {
      install("six")
    }
  }
}

$extraKts

dependencies {
  implementation("androidx.core:core-ktx:1.13.0")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("com.google.android.material:material:1.12.0")
$deps
}
""".trim()
    } else {
      """
plugins {
  id 'com.android.application'
  id 'org.jetbrains.kotlin.android'
  id 'com.chaquo.python'
}

android {
  namespace '$pkg'
  compileSdk $COMPILE_SDK

  defaultConfig {
    applicationId "$pkg"
    minSdk $MIN_SDK
    targetSdk $TARGET_SDK
    versionCode 1
    versionName "1.0"
  }
}

chaquopy {
  defaultConfig {
    pip {
      install "six"
    }
  }
}

$extraGroovy

dependencies {
  implementation 'androidx.core:core-ktx:1.13.0'
  implementation 'androidx.appcompat:appcompat:1.7.1'
  implementation 'com.google.android.material:material:1.12.0'
$deps
}
""".trim()
    }

private fun versionCatalog() =
    """
[versions]
agp = "$AGP_VERSION"
kotlin = "$KOTLIN_VERSION"
chaquopy = "$CHAQUOPY_VERSION"

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
chaquopy = { id = "com.chaquo.python", version.ref = "chaquopy" }
""".trim()

private fun androidManifest() =
    """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application
      android:name="com.chaquo.python.android.PyApplication"
      android:allowBackup="true"
      android:label="@string/app_name"
      android:supportsRtl="true"
      android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">
    <activity android:name=".MainActivity" android:exported="true">
      <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
      </intent-filter>
    </activity>
  </application>
</manifest>""".trim()

private fun pythonMain() =
    """def get_message(name="Chaquopy"):
    return f"Hello from Python, {name}!"
""".trim()
