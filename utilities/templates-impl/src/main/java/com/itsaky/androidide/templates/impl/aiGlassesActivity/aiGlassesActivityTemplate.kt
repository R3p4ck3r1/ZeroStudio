package com.itsaky.androidide.templates.impl.aiGlassesActivity

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.templates.Language.Kotlin
import com.itsaky.androidide.templates.ProjectTemplate
import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.composeDependencies
import com.itsaky.androidide.templates.base.models.Dependency
import com.itsaky.androidide.templates.base.models.parseDependency
import com.itsaky.androidide.templates.base.modules.android.ManifestActivity
import com.itsaky.androidide.templates.base.modules.android.defaultAppModule
import com.itsaky.androidide.templates.base.util.AndroidModuleResManager.ResourceType.VALUES
import com.itsaky.androidide.templates.base.util.SourceWriter
import com.itsaky.androidide.templates.impl.R
import com.itsaky.androidide.templates.impl.base.createRecipe
import com.itsaky.androidide.templates.impl.base.writeMainActivity
import com.itsaky.androidide.templates.impl.baseProjectImpl

fun aiGlassesActivityProject(): ProjectTemplate = baseProjectImpl {
  templateName = R.string.template_ai_glasses_activity
  thumb = R.drawable.template_empty_activity
  description = string.title_template_description_ai_glasses_activity

  defaultAppModule(addAndroidX = false) {
    isComposeModule = true

    recipe = createRecipe {
      require(data.language == Kotlin) { "AI Glasses template requires Kotlin" }

      composeDependencies()
      executor.apply {
        addDependency(Dependency.AndroidX.Compose.LifeCycle_Runtime_Ktx)
        addDependency(parseDependency("androidx.xr.glimmer:glimmer:1.0.0-alpha02"))
        addDependency(parseDependency("androidx.xr.projected:projected:1.0.0-alpha03"))
      }

      sources { writeAiGlassesSources(this) }
      res { writeXmlResource("strings", VALUES, source = ::aiGlassesStringsXml) }
      writeAiGlassesManifest()
    }
  }
}

private fun AndroidModuleTemplateBuilder.writeAiGlassesSources(writer: SourceWriter) {
  writeMainActivity(writer, ktSrc = ::mainActivityKt, javaSrc = { "" })
  writeKtSrc(data.packageName, "GlassesMainActivity", source = ::glassesActivityKt)
  writeKtSrc(data.packageName, "AudioInterface", source = ::audioInterfaceKt)
}

private fun AndroidModuleTemplateBuilder.writeAiGlassesManifest() {
  manifest {
    addActivity(ManifestActivity(name = "MainActivity", isExported = true, isLauncher = true))
    addActivity(
        ManifestActivity(
            name = "GlassesMainActivity",
            isExported = true,
            configureAttrs = { attribute("android:requiredDisplayCategory", "@string/display_category_xr_projected") },
        )
    )
  }
}

private fun AndroidModuleTemplateBuilder.mainActivityKt() =
    """
@file:OptIn(ExperimentalProjectedApi::class)

package ${data.packageName}

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { ConnectionScreen() }
  }
}

@Composable
private fun ConnectionScreen() {
  val context = LocalContext.current
  Scaffold { paddingValues ->
    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Text(text = stringResource(id = R.string.hello_ai_glasses), style = MaterialTheme.typography.titleLarge)
      Spacer(modifier = Modifier.height(32.dp))
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        val scope = rememberCoroutineScope()
        val isConnected by ProjectedContext.isProjectedDeviceConnected(context, scope.coroutineContext)
            .collectAsStateWithLifecycle(initialValue = false)

        Button(
            onClick = {
              val options = ProjectedContext.createProjectedActivityOptions(context)
              context.startActivity(Intent(context, GlassesMainActivity::class.java), options.toBundle())
            },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
            enabled = isConnected,
        ) {
          Text(text = stringResource(id = R.string.launch), style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text =
                stringResource(id = R.string.status_prefix) +
                    if (isConnected) stringResource(id = R.string.status_connected)
                    else stringResource(id = R.string.status_disconnected),
            style = MaterialTheme.typography.titleMedium,
        )
      } else {
        Text(text = stringResource(id = R.string.unsupported_android_version), style = MaterialTheme.typography.titleMedium)
      }
    }
  }
}
""".trim()

private fun AndroidModuleTemplateBuilder.glassesActivityKt() =
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

private fun AndroidModuleTemplateBuilder.audioInterfaceKt() =
    """
package ${data.packageName}

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AudioInterface(private val context: Context, private val initializationMessage: String) :
    DefaultLifecycleObserver {
  private lateinit var tts: TextToSpeech

  override fun onStart(owner: LifecycleOwner) {
    super.onStart(owner)
    tts = TextToSpeech(context) { status ->
      if (status == TextToSpeech.SUCCESS) speak(initializationMessage)
      else Log.e(TAG, "Initialization failed with status: $status")
    }
  }

  fun speak(textToSpeak: String) {
    tts.speak(textToSpeak, TextToSpeech.QUEUE_ADD, null, initializationMessage.lowercase().replace(" ", "_"))
  }

  override fun onStop(owner: LifecycleOwner) {
    super.onStop(owner)
    tts.shutdown()
  }

  companion object {
    private const val TAG = "AudioInterface"
  }
}
""".trim()

private fun aiGlassesStringsXml() =
    """
<resources>
    <string name="app_name">Basic AI Glasses Activity</string>
    <string name="display_category_xr_projected">com.google.android.glasses.category.PROJECTED</string>
    <string name="hello_ai_glasses">Hello, AI Glasses!</string>
    <string name="launch">Launch</string>
    <string name="status_prefix">Status: </string>
    <string name="status_connected">Connected</string>
    <string name="status_disconnected">Disconnected</string>
    <string name="unsupported_android_version">This version of Android is not supported</string>
    <string name="close">Close</string>
</resources>
""".trim()
