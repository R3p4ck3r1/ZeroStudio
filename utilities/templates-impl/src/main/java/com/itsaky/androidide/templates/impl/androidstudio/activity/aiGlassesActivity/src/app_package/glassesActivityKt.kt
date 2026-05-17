package com.itsaky.androidide.templates.impl.androidstudio.activity.aiGlassesActivity.src.app_package

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

internal fun AndroidModuleTemplateBuilder.glassesActivityKt() =
    """
package ${data.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.xr.glimmer.Button
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.surface

class GlassesMainActivity : ComponentActivity() {
  private lateinit var audioInterface: AudioInterface

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    audioInterface = AudioInterface(this, getString(R.string.hello_ai_glasses))
    lifecycle.addObserver(audioInterface)
    setContent {
      GlimmerTheme {
        HomeScreen(onClose = {
          audioInterface.speak("Goodbye!")
          finish()
        })
      }
    }
  }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier, onClose: () -> Unit) {
  Box(modifier = modifier.surface(focusable = false).fillMaxSize(), contentAlignment = Alignment.Center) {
    Card(
        title = { Text(stringResource(id = R.string.app_name)) },
        action = { Button(onClick = onClose) { Text(stringResource(id = R.string.close)) } }) {
          Text(stringResource(id = R.string.hello_ai_glasses))
        }
  }
}
""".trim()
