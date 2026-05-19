package android.zero.studio.layouteditor

import android.content.Context
import android.os.Build

object LayoutEditorContext {
  @Volatile private var appContext: Context? = null

  fun init(context: Context) {
    if (appContext == null) {
      appContext = context.applicationContext
    }
  }

  val context: Context
    get() = requireNotNull(appContext) { "LayoutEditorContext is not initialized" }

  val isAtLeastTiramisu: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
