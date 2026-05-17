package com.itsaky.androidide.templates.impl.chaquopy.compose

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

fun chaquopyComposeDemoProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_chaquopy_compose
  thumb = R.drawable.template_compose_empty_activity
  description = string.title_template_description_chaquopy_compose

  defaultAppModule {
    recipe = createRecipe {
      sources { writeMainActivity(this, ::mainActivityKt, ::mainActivityJava) }
      writeChaquopyTemplate(
          ChaquopyTemplateSpec(
              uiKind = ChaquopyUiKind.Compose,
              pythonModuleName = "main",
              pythonMessageArgument = "Compose Demo",
          )
      )
    }
  }
}

private fun com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder.mainActivityKt() =
    """package ${data.packageName}

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.chaquo.python.Python

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val message = Python.getInstance().getModule("main").callAttr("get_message", "Compose Demo").toString()

    setContent {
      MaterialTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(text = message)
        }
      }
    }
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
    textView.setText(Python.getInstance().getModule("main").callAttr("get_message", "Compose Java host").toString());
    setContentView(textView);
  }
}
"""
