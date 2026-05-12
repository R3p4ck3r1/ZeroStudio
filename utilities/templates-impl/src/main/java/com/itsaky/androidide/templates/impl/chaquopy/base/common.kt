package com.itsaky.androidide.templates.impl.chaquopy.base

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import java.io.File

internal fun AndroidModuleTemplateBuilder.writeChaquopyCommonFiles(appDependencies: String, extraKts: String = "", extraGroovy: String = "") {
  val appDir = File(data.projectDir, "app")
  val mainDir = File(appDir, "src/main")
  executor.save(androidManifest(), File(mainDir, "AndroidManifest.xml"))
  executor.save(pyMain(), File(mainDir, "python/main.py"))
  executor.save("", File(mainDir, "python/chaquopy/__init__.py"))
  executor.save("", File(mainDir, "python/chaquopy/utils/__init__.py"))
  executor.save(pyConsole(), File(mainDir, "python/chaquopy/utils/console.py"))
  executor.save(rootGradle(data.useKts), File(data.projectDir, if (data.useKts) "build.gradle.kts" else "build.gradle"))
  executor.save(appGradle(data.useKts, data.packageName, appDependencies, extraKts, extraGroovy), File(appDir, if (data.useKts) "build.gradle.kts" else "build.gradle"))
  if (data.useToml) executor.save(versionCatalog(), File(data.projectDir, "gradle/libs.versions.toml"))
}

private fun rootGradle(kts: Boolean) = if (kts) """
plugins {
  id("com.android.application") version "8.4.2" apply false
  id("com.chaquo.python") version "17.0.0" apply false
  id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}
""".trim() else """
plugins {
  id 'com.android.application' version '8.4.2' apply false
  id 'com.chaquo.python' version '17.0.0' apply false
  id 'org.jetbrains.kotlin.android' version '1.9.0' apply false
}
""".trim()

private fun appGradle(kts: Boolean, pkg: String, deps: String, extraKts: String, extraGroovy: String) = if (kts) """
plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.kapt")
  id("com.chaquo.python")
}
android {
  namespace = "$pkg"
  compileSdk = 36
  defaultConfig {
    applicationId = "$pkg"
    minSdk = 24
    targetSdk = 36
  }
}
chaquopy { defaultConfig { pip { install("six") } } }
$extraKts
dependencies {
  implementation("androidx.appcompat:appcompat:1.7.1")
$deps
}
""".trim() else """
plugins {
  id 'com.android.application'
  id 'org.jetbrains.kotlin.android'
  id 'org.jetbrains.kotlin.kapt'
  id 'com.chaquo.python'
}
android {
  namespace '$pkg'
  compileSdk 36
  defaultConfig { applicationId "$pkg"; minSdk 24; targetSdk 36 }
}
chaquopy { defaultConfig { pip { install "six" } } }
$extraGroovy
dependencies {
  implementation 'androidx.appcompat:appcompat:1.7.1'
$deps
}
""".trim()

private fun versionCatalog() = """
[plugins]
androidApplication = { id = "com.android.application", version = "8.4.2" }
chaquopy = { id = "com.chaquo.python", version = "17.0.0" }
""".trim()

private fun androidManifest() = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:name="com.chaquo.python.android.PyApplication" android:allowBackup="true" android:label="@string/app_name" android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
        </activity>
    </application>
</manifest>""".trim()

private fun pyMain() = """from six.moves import input

def main():
    print("Enter your name, or an empty line to exit.")
    while True:
        try:
            name = input()
        except EOFError:
            break
        if not name:
            break
        print("Hello {}!".format(name))
""".trim()

private fun pyConsole() = """from io import TextIOBase
from queue import Queue
class ConsoleInputStream(TextIOBase):
    def __init__(self, task):
        self.task = task
        self.queue = Queue()
""".trim()
