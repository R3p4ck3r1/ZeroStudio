package com.itsaky.androidide.templates.impl.lithoCompose

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.composeDependencies
import com.itsaky.androidide.templates.base.lithoClassicDependencies
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.writeMainActivity
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun lithoComposeProject(): ProjectTemplate = baseProjectImpl {
  templateName = string.template_litho_compose
  thumb = R.drawable.template_compose_empty_activity
  description = string.title_template_description_litho_compose
  defaultAppModule(addAndroidX = false) {
    isComposeModule = true
    recipe = createRecipe {
      require(data.language == Language.Kotlin)
      composeDependencies()
      lithoClassicDependencies()
      sources { writeMainActivity(this, ::composeMainActivityKt, javaSrc = { "" }) }
    }
  }
}

private fun AndroidModuleTemplateBuilder.composeMainActivityKt() =
    """
package ${data.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.facebook.litho.ComponentContext
import com.facebook.litho.LithoView
import com.facebook.litho.widget.Text as LithoText
import com.facebook.soloader.SoLoader

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SoLoader.init(this, false)
    setContent { Demo() }
  }
}

@Composable
fun Demo() {
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    AndroidView(factory = { context ->
      val c = ComponentContext(context)
      LithoView.create(
        context,
        LithoText.create(c)
          .text("Hello Litho in Compose")
          .textSizeDip(28f)
          .build(),
      )
    })
    Text("Compose host + Litho view ready")
  }
}
"""
