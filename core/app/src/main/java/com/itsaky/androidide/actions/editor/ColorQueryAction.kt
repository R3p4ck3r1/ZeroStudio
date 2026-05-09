package com.itsaky.androidide.actions.editor

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import android.zero.studio.ui.colorpicker.dialog.ColorPickerRingDiamondHEXDialog
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.ActionItem
import com.itsaky.androidide.actions.BaseEditorAction
import com.itsaky.androidide.actions.EditorActivityAction
import com.itsaky.androidide.resources.R

private fun resolveSelection(data: ActionData): String? {
  val editor = data.get(com.itsaky.androidide.editor.ui.IDEEditor::class.java) ?: return null
  if (!editor.cursor.isSelected) return null
  val left = editor.cursor.left().index
  val right = editor.cursor.right().index
  if (right <= left) return null
  return editor.text.subSequence(left, right).toString()
}

private fun showColorQueryDialog(context: Context, query: String?) {
  val dialog = ComponentDialog(context)
  dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
  dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
  val composeView =
      ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          MaterialTheme {
            ColorPickerRingDiamondHEXDialog(
                initialColor = Color.White,
                initialQuery = query,
            ) { _, _ ->
              dialog.dismiss()
            }
          }
        }
      }
  dialog.setContentView(composeView)
  dialog.show()
}

class ColorQueryTextAction(context: Context, override val order: Int) : BaseEditorAction() {
  override val id: String = "ide.editor.color.query.text"

  init {
    icon = ContextCompat.getDrawable(context, R.drawable.ic_color_palette)
    label = context.getString(R.string.action_color_query)
    location = ActionItem.Location.EDITOR_TEXT_ACTIONS
  }

  override suspend fun execAction(data: ActionData): Boolean {
    val ctx = getContext(data) ?: return false
    showColorQueryDialog(ctx, resolveSelection(data))
    return true
  }
}

class ColorQueryToolbarAction(private val context: Context, override val order: Int) : EditorActivityAction() {
  override val id: String = "ide.editor.color.query.toolbar"

  init {
      icon = ContextCompat.getDrawable(context, R.drawable.ic_color_palette)
    label = context.getString(R.string.action_color_query)
    location = ActionItem.Location.EDITOR_TOOLBAR
  }

  override suspend fun execAction(data: ActionData): Any {
    val activity = data.getActivity() ?: return false
    val query = activity.getCurrentEditor()?.editor?.let {
      if (it.cursor.isSelected) {
        val left = it.cursor.left().index
        val right = it.cursor.right().index
        if (right > left) it.text.subSequence(left, right).toString() else null
      } else null
    }
    showColorQueryDialog(activity, query)
    return true
  }
}
