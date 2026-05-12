package com.itsaky.androidide.templates.impl.chaquopy

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.LAYOUT
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.emptyThemesAndColors
import com.itsaky.androidide.templates.impl.base.writeMainActivity
import com.itsaky.androidide.templates.impl.baseProjectImpl
import java.io.File

private enum class UiStack { Xml, Compose, Litho }

fun chaquopyXmlDemoProject(): ProjectTemplate = chaquopyProject(UiStack.Xml)
fun chaquopyComposeDemoProject(): ProjectTemplate = chaquopyProject(UiStack.Compose)
fun chaquopyLithoDemoProject(): ProjectTemplate = chaquopyProject(UiStack.Litho)

private fun chaquopyProject(uiStack: UiStack): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_empty
  thumb = R.drawable.template_empty_activity
  description = string.title_template_description_basicactivity
  defaultAppModule {
    recipe = createRecipe {
      sources { writeMainActivity(this, { mainKt(uiStack, data.packageName) }, { mainJava(uiStack, data.packageName) }) }
      if (uiStack == UiStack.Xml) res { writeXmlResource("activity_main", LAYOUT, source = ::activityMainXml) }
      res { emptyThemesAndColors() }
      val appDir = File(data.projectDir, "app")
      val mainDir = File(appDir, "src/main")
      save(androidManifest(), File(mainDir, "AndroidManifest.xml"))
      save(pyMain(), File(mainDir, "python/main.py"))
      save("", File(mainDir, "python/chaquopy/__init__.py"))
      save("", File(mainDir, "python/chaquopy/utils/__init__.py"))
      save(pyConsole(), File(mainDir, "python/chaquopy/utils/console.py"))
      save(rootBuildGradle(data.useKts), File(data.projectDir, if (data.useKts) "build.gradle.kts" else "build.gradle"))
      save(appBuildGradle(data.useKts, uiStack, data.packageName), File(appDir, if (data.useKts) "build.gradle.kts" else "build.gradle"))
      if (data.useToml) save(versionCatalog(), File(data.projectDir, "gradle/libs.versions.toml"))
    }
  }
}

private fun rootBuildGradle(useKts: Boolean) = if (useKts) """
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

private fun appBuildGradle(useKts: Boolean, uiStack: UiStack, packageName: String): String {
  val composeDep = if (uiStack == UiStack.Compose) "implementation \"androidx.activity:activity-compose:1.10.1\"" else ""
  val lithoDeps = if (uiStack == UiStack.Litho) "implementation \"com.facebook.litho:litho-core:0.52.0\"\n    kapt \"com.facebook.litho:litho-processor:0.52.0\"" else ""
  return if (useKts) """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.chaquo.python")
}
android {
    namespace = "$packageName"
    compileSdk = 36
    defaultConfig { applicationId = "$packageName"; minSdk = 24; targetSdk = 36 }
}
chaquopy {
    defaultConfig { pip { install("six") } }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    ${composeDep.replace('"','\"').replace('implementation','implementation(').replace(':1.10.1\\"',' :1.10.1\\")')}
    ${if (uiStack == UiStack.Litho) "implementation(\"com.facebook.litho:litho-core:0.52.0\")\n    kapt(\"com.facebook.litho:litho-processor:0.52.0\")" else ""}
}
""".trim() else """
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'org.jetbrains.kotlin.kapt'
    id 'com.chaquo.python'
}
android {
    namespace '$packageName'
    compileSdk 36
    defaultConfig { applicationId "$packageName"; minSdk 24; targetSdk 36 }
}
chaquopy {
    defaultConfig { pip { install "six" } }
}
dependencies {
    implementation 'androidx.appcompat:appcompat:1.7.1'
    $composeDep
    $lithoDeps
}
""".trim()
}

private fun versionCatalog() = """
[versions]
appcompat = "1.7.1"
[plugins]
androidApplication = { id = "com.android.application", version = "8.4.2" }
chaquopy = { id = "com.chaquo.python", version = "17.0.0" }
""".trim()

private fun androidManifest() = """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name="com.chaquo.python.android.PyApplication"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trim()

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
        self.buffer = ""

    def on_input(self, value):
        self.queue.put(value)
""".trim()

private fun mainKt(uiStack: UiStack, packageName: String) = """
package $packageName

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ${if (uiStack == UiStack.Xml) "setContentView(R.layout.activity_main)" else "setContentView(TextView(this).apply { text = \"Chaquopy ${uiStack.name} demo\" })"}
        Python.getInstance().getModule("main").callAttr("main")
    }
}
""".trim()

private fun mainJava(uiStack: UiStack, packageName: String) = """
package $packageName;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.chaquo.python.Python;

public class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ${if (uiStack == UiStack.Xml) "setContentView(R.layout.activity_main);" else "TextView tv = new TextView(this); tv.setText(\"Chaquopy ${uiStack.name} demo\"); setContentView(tv);"}
        Python.getInstance().getModule("main").callAttr("main");
    }
}
""".trim()

private fun activityMainXml() = """
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="Chaquopy XML Demo" />
</FrameLayout>
""".trim()
