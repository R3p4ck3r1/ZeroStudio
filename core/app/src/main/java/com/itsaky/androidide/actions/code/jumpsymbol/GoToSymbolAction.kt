package com.itsaky.androidide.actions.code.jumpsymbol

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.actions.requireContext
import com.itsaky.androidide.resources.R
import com.itsaky.androidide.tasks.launchAsyncWithProgress
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parses the current file and displays a bottom sheet with code symbols.
 *
 * Java and Kotlin symbols are resolved with the generated ANTLR lexers/parsers; other supported
 * languages continue to use the Tree-Sitter fallback.
 *
 * @author android_zero
 */
class GoToSymbolAction(context: Context, override val order: Int) : EditorRelatedAction() {

  override val id: String = "ide.editor.cursor.go_to_symbol"

  init {
    label = context.getString(R.string.action_show_code_outline)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_edit_code_outline_action)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)
    val editor = data.getEditor()
    visible = editor != null
    enabled = visible
  }

  override suspend fun execAction(data: ActionData): Boolean {
    val editor = data.getEditor() ?: return false
    val context = data.requireContext()
    val activity = context as? AppCompatActivity ?: return false
    val file = data.get(File::class.java) ?: return false

    activity.lifecycleScope.launchAsyncWithProgress(
        configureFlashbar = { builder, _ -> builder.message("Parsing symbols...") }
    ) { _, _ ->
      val code = editor.text.toString()

      val symbols =
          withContext(Dispatchers.IO) { TreeSitterSymbolResolver.parseSymbols(context, file, code) }

      withContext(Dispatchers.Main) {
        if (symbols.isNotEmpty()) {
          val sheet =
              SymbolListBottomSheet(symbols) { symbol ->
                editor.setSelection(symbol.line, 0)
                editor.ensurePositionVisible(symbol.line, 0)
                editor.cursorAnimator.start()
              }
          sheet.show(activity.supportFragmentManager, "SymbolListBottomSheet")
        } else {
          com.itsaky.androidide.utils.flashInfo(context.getString(R.string.msg_no_symbols_found))
        }
      }
    }
    return true
  }
}
