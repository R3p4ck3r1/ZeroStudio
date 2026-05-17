package com.itsaky.androidide.templates.impl.chaquopy.litho

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.writeMainActivity
import com.itsaky.androidide.templates.impl.baseProjectImpl
import com.itsaky.androidide.templates.impl.chaquopy.base.ChaquopyTemplateSpec
import com.itsaky.androidide.templates.impl.chaquopy.base.ChaquopyUiKind
import com.itsaky.androidide.templates.impl.chaquopy.base.writeChaquopyTemplate

fun chaquopyLithoDemoProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_chaquopy_litho
  thumb = R.drawable.template_basic_activity
  description = string.title_template_description_chaquopy_litho

  defaultAppModule {
    recipe = createRecipe {
      sources { writeMainActivity(this, ::mainActivityKt, ::mainActivityJava) }
      writeChaquopyTemplate(
          ChaquopyTemplateSpec(
              uiKind = ChaquopyUiKind.Litho,
              pythonModuleName = "main",
              pythonMessageArgument = "Litho Demo",
          )
      )
    }
  }
}

private fun com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder.mainActivityKt() =
    """package ${data.packageName}

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val message = Python.getInstance().getModule("main").callAttr("get_message", "Litho Demo").toString()
    setContentView(TextView(this).apply { text = message })
  }
}
"""

private fun com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder.mainActivityJava() =
    """package ${data.packageName};

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.chaquo.python.Python;

public class MainActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    TextView textView = new TextView(this);
    textView.setText(Python.getInstance().getModule("main").callAttr("get_message", "Litho Demo").toString());
    setContentView(textView);
  }
}
"""
