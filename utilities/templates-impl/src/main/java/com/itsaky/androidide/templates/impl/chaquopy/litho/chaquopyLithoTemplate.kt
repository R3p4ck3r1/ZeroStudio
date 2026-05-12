package com.itsaky.androidide.templates.impl.chaquopy.litho

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.emptyThemesAndColors
import com.itsaky.androidide.templates.impl.base.writeMainActivity
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.impl.chaquopy.base.writeChaquopyCommonFiles

fun chaquopyLithoDemoProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_chaquopy_litho
  thumb = R.drawable.template_basic_activity
  description = string.title_template_description_chaquopy_litho
  defaultAppModule {
    recipe = createRecipe {
      sources { writeMainActivity(this, ::mainActivityKt, ::mainActivityJava) }
      res { emptyThemesAndColors() }
      writeChaquopyCommonFiles("  implementation \"com.facebook.litho:litho-core:0.52.0\"\n  kapt \"com.facebook.litho:litho-processor:0.52.0\"")
    }
  }
}

private fun com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder.mainActivityKt() = """package ${data.packageName}
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
class MainActivity : AppCompatActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(TextView(this).apply { text = \"Chaquopy Litho Demo\" }); Python.getInstance().getModule(\"main\").callAttr(\"main\") }
}"""
private fun com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder.mainActivityJava() = """package ${data.packageName};
import android.os.Bundle; import android.widget.TextView; import androidx.appcompat.app.AppCompatActivity; import com.chaquo.python.Python;
public class MainActivity extends AppCompatActivity { @Override protected void onCreate(Bundle b){ super.onCreate(b); TextView tv=new TextView(this); tv.setText(\"Chaquopy Litho Demo\"); setContentView(tv); Python.getInstance().getModule(\"main\").callAttr(\"main\"); }}"""
