package com.itsaky.androidide.templates.impl.chaquopy.compose

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.emptyThemesAndColors
import com.itsaky.androidide.templates.impl.base.writeMainActivity
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.impl.chaquopy.base.writeChaquopyCommonFiles

fun chaquopyComposeDemoProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_compose
  thumb = R.drawable.template_compose_empty_activity
  description = string.title_template_description_basicactivity
  defaultAppModule {
    recipe = createRecipe {
      sources { writeMainActivity(this, ::mainActivityKt, ::mainActivityJava) }
      res { emptyThemesAndColors() }
      writeChaquopyCommonFiles("  implementation \"androidx.activity:activity-compose:1.10.1\"\n  implementation \"androidx.compose.material3:material3:1.3.2\"", extraKts = "buildFeatures { compose = true }", extraGroovy = "buildFeatures { compose true }")
    }
  }
}

private fun com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder.mainActivityKt() = """package ${data.packageName}
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import com.chaquo.python.Python
class MainActivity : AppCompatActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { Text(\"Chaquopy Compose Demo\") }; Python.getInstance().getModule(\"main\").callAttr(\"main\") }
}"""
private fun com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder.mainActivityJava() = """package ${data.packageName};
import android.os.Bundle; import android.widget.TextView; import androidx.appcompat.app.AppCompatActivity; import com.chaquo.python.Python;
public class MainActivity extends AppCompatActivity { @Override protected void onCreate(Bundle b){ super.onCreate(b); TextView tv=new TextView(this); tv.setText(\"Chaquopy Compose Demo (Java host)\"); setContentView(tv); Python.getInstance().getModule(\"main\").callAttr(\"main\"); }}"""
