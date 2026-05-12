package com.itsaky.androidide.templates.impl.chaquopy.xml

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.LAYOUT
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.emptyThemesAndColors
import com.itsaky.androidide.templates.impl.base.writeMainActivity
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.impl.chaquopy.base.writeChaquopyCommonFiles

fun chaquopyXmlDemoProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_chaquopy_xml
  thumb = R.drawable.template_empty_activity
  description = string.title_template_description_chaquopy_xml
  defaultAppModule {
    recipe = createRecipe {
      sources { writeMainActivity(this, ::mainActivityKt, ::mainActivityJava) }
      res { writeXmlResource("activity_main", LAYOUT, source = ::activityMainXml); emptyThemesAndColors() }
      writeChaquopyCommonFiles(appDependencies = "")
    }
  }
}

private fun com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder.mainActivityKt() = """package ${data.packageName}
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    Python.getInstance().getModule("main").callAttr("main")
  }
}"""
private fun com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder.mainActivityJava() = """package ${data.packageName};
import android.os.Bundle; import androidx.appcompat.app.AppCompatActivity; import com.chaquo.python.Python;
public class MainActivity extends AppCompatActivity { @Override protected void onCreate(Bundle b){ super.onCreate(b); setContentView(R.layout.activity_main); Python.getInstance().getModule("main").callAttr("main"); }}"""
private fun activityMainXml() = """<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\"><TextView android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:layout_gravity=\"center\" android:text=\"Chaquopy XML Demo\"/></FrameLayout>"""
