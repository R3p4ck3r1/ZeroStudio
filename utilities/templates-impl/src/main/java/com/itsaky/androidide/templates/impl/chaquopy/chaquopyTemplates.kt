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

fun chaquopyXmlDemoProject(): ProjectTemplate = chaquopyProject("xml")
fun chaquopyComposeDemoProject(): ProjectTemplate = chaquopyProject("compose")
fun chaquopyLithoDemoProject(): ProjectTemplate = chaquopyProject("litho")

private fun chaquopyProject(flavor: String): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_empty
  thumb = R.drawable.template_empty_activity
  description = string.title_template_description_basicactivity
  defaultAppModule {
    recipe = createRecipe {
      sources { writeMainActivity(this, { mainKt(flavor) }, { mainJava(flavor) }) }
      if (flavor == "xml") res { writeXmlResource("activity_main", LAYOUT, source = ::xmlLayout) }
      res { emptyThemesAndColors() }
      val appDir = File(data.projectDir, "app")
      val mainDir = File(appDir, "src/main")
      save(manifestSrc(), File(mainDir, "AndroidManifest.xml"))
      save(pyMainSrc(), File(mainDir, "python/main.py"))
      save("", File(mainDir, "python/chaquopy/__init__.py"))
      save("", File(mainDir, "python/chaquopy/utils/__init__.py"))
      save(pyConsoleSrc(), File(mainDir, "python/chaquopy/utils/console.py"))
      save(topGradle(data.useKts), File(data.projectDir, if (data.useKts) "build.gradle.kts" else "build.gradle"))
      save(appGradle(data.useKts, flavor), File(appDir, if (data.useKts) "build.gradle.kts" else "build.gradle"))
      if (data.useToml) save(versionCatalog(), File(data.projectDir, "gradle/libs.versions.toml"))
    }
  }
}

private fun topGradle(kts:Boolean)= if(kts) """plugins { id("com.android.application") version "8.4.2" apply false; id("com.chaquo.python") version "17.0.0" apply false; id("org.jetbrains.kotlin.android") version "1.9.0" apply false }""" else """plugins { id 'com.android.application' version '8.4.2' apply false; id 'com.chaquo.python' version '17.0.0' apply false; id 'org.jetbrains.kotlin.android' version '1.9.0' apply false }"""
private fun appGradle(kts:Boolean, flavor: String)= if(kts) """plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("com.chaquo.python") }
android { namespace = "com.example.chaquopy"; compileSdk = 36; defaultConfig { applicationId = "com.example.chaquopy"; minSdk = 24; targetSdk = 36 } }
chaquopy { defaultConfig { pip { install("six") } } }
dependencies { implementation("androidx.appcompat:appcompat:1.7.1") ${if (flavor=="compose") "implementation(\"androidx.activity:activity-compose:1.10.1\")" else ""} }""" else """plugins { id 'com.android.application'; id 'org.jetbrains.kotlin.android'; id 'com.chaquo.python' }
android { namespace 'com.example.chaquopy'; compileSdk 36; defaultConfig { applicationId "com.example.chaquopy"; minSdk 24; targetSdk 36 } }
chaquopy { defaultConfig { pip { install "six" } } }
dependencies { implementation 'androidx.appcompat:appcompat:1.7.1' ${if (flavor=="compose") "implementation 'androidx.activity:activity-compose:1.10.1'" else ""} }"""
private fun versionCatalog() = """[versions]
appcompat = "1.7.1"
[plugins]
androidApplication = { id = "com.android.application", version = "8.4.2" }
chaquopy = { id = "com.chaquo.python", version = "17.0.0" }"""
private fun manifestSrc() = """<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:name="com.chaquo.python.android.PyApplication" android:label="@string/app_name"><activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>"""
private fun pyMainSrc() = """from six.moves import input

def main():
    print("Enter your name, or an empty line to exit.")
    while True:
        try:
            name = input()
        except EOFError:
            break
        if not name:
            break
        print("Hello {}!".format(name))"""
private fun pyConsoleSrc() = "from io import TextIOBase\nfrom queue import Queue\n# simplified console bridge\n"
private fun mainKt(flavor: String) = """package com.example.chaquopy
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import android.widget.TextView
class MainActivity : AppCompatActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); ${if(flavor=="xml")"setContentView(R.layout.activity_main)" else "val t=TextView(this); t.text=\"Chaquopy $flavor demo\"; setContentView(t)"}; Python.getInstance().getModule("main").callAttr("main") } }"""
private fun mainJava(flavor: String) = """package com.example.chaquopy; import android.os.Bundle; import androidx.appcompat.app.AppCompatActivity; import com.chaquo.python.Python; import android.widget.TextView; public class MainActivity extends AppCompatActivity { protected void onCreate(Bundle b){ super.onCreate(b); ${if(flavor=="xml")"setContentView(R.layout.activity_main);" else "TextView t=new TextView(this);t.setText(\"Chaquopy $flavor demo\");setContentView(t);"} Python.getInstance().getModule("main").callAttr("main"); }}"""
private fun xmlLayout() = """<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent"><TextView android:layout_gravity="center" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Chaquopy XML Demo"/></FrameLayout>"""
