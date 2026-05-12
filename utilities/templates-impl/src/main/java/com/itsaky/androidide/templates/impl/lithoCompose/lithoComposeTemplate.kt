package com.itsaky.androidide.templates.impl.lithoCompose

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.Language
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.composeDependencies
import com.itsaky.androidide.templates.base.lithoClassicDependencies
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.base.util.SourceWriter
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.writeMainActivity
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun lithoComposeProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_compose
  thumb = R.drawable.template_compose_empty_activity
  description = string.title_test
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

private fun AndroidModuleTemplateBuilder.composeMainActivityKt() = """
package ${data.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { Demo() }
  }
}

@Composable
fun Demo() { Text("Compose + Litho template ready") }
"""
