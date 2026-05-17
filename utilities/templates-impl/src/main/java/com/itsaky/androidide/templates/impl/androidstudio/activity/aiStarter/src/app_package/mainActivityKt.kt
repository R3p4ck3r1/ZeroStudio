package com.itsaky.androidide.templates.impl.androidstudio.activity.aiStarter.src.app_package

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder

internal fun AndroidModuleTemplateBuilder.mainActivityKt() =
    """
package ${data.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ${data.packageName}.ui.theme.${data.name}Theme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ${data.name}Theme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          Greeting(name = "Android", modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  ${data.name}Theme {
    Greeting("Android")
  }
}
""".trim()
