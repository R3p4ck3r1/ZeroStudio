package com.itsaky.androidide.activities.editor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.toArgb
import com.smarttoolfactory.colorpicker.dialog.ColorPickerRingDiamondHEXDialog

class ColorPickerQueryActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val initial = parseColor(intent?.getStringExtra(EXTRA_QUERY))
    setContent {
      ColorPickerRingDiamondHEXDialog(initialColor = androidx.compose.ui.graphics.Color(initial)) { color, _ ->
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_ARGB, color.toArgb()))
        finish()
      }
    }
  }

  private fun parseColor(raw: String?): Int {
    val candidate = raw?.trim().orEmpty()
    if (candidate.isEmpty()) return Color.WHITE
    return try {
      Color.parseColor(if (candidate.startsWith("#")) candidate else "#$candidate")
    } catch (_: Throwable) {
      Color.WHITE
    }
  }

  companion object {
    const val EXTRA_QUERY = "extra_query"
    const val EXTRA_RESULT_ARGB = "extra_result_argb"

    fun createIntent(context: Context, query: String?): Intent {
      return Intent(context, ColorPickerQueryActivity::class.java).putExtra(EXTRA_QUERY, query)
    }
  }
}
