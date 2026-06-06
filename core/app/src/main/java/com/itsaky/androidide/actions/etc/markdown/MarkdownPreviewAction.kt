package com.itsaky.androidide.actions.etc.markdown

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.KeyboardUtils
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.actions.EditorRelatedAction
import com.itsaky.androidide.actions.markInvisible
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.fragments.editor.FragmentTabEntry
import com.itsaky.androidide.fragments.editor.FragmentTabRegistry
import com.itsaky.androidide.fragments.editor.markdown.MarkdownPreviewFragment
import com.itsaky.androidide.resources.R
import java.io.File

/**
 * Action menu item for previewing Markdown files in a tab.
 *
 * This action opens a Markdown preview tab in the EditorHandlerActivity
 * when the user clicks the preview button in the editor toolbar.
 *
 * @author ZeroStudio
 */
class MarkdownPreviewAction(context: Context, override val order: Int) : EditorRelatedAction() {

  override val id: String = ID

  override var requiresUIThread: Boolean = false

  companion object {
    const val ID = "ide.editor.markdownPreview"
  }

  init {
    label = context.getString(R.string.title_markdown_preview)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_markdown_preview)
  }

  override fun prepare(data: ActionData) {
    super.prepare(data)

    val activity = data.getActivity() as? EditorHandlerActivity
    if (activity == null) {
      markInvisible()
      return
    }

    // Check if there's an open file with a Markdown extension
    val editor = data.getEditor()
    val file = editor?.file

    if (file != null) {
      val extension = file.extension.lowercase()
      val markdownExtensions = listOf("md", "mdr", "markdown", "mdx", "mkd", "mkdn")
      if (extension in markdownExtensions) {
        visible = true
        enabled = true
      } else {
        // Check if any Markdown preview tab is registered
        val entries = FragmentTabRegistry.getByFileExtension(extension)
        if (entries.isNotEmpty()) {
          visible = true
          enabled = true
        } else {
          markInvisible()
        }
      }
    } else {
      // No file open, check if we can open a generic Markdown preview
      visible = true
      enabled = false // No file to preview
    }
  }

  override fun getShowAsActionFlags(data: ActionData): Int {
    val activity = data.getActivity() ?: return super.getShowAsActionFlags(data)
    return if (KeyboardUtils.isSoftInputVisible(activity)) {
      MenuItem.SHOW_AS_ACTION_IF_ROOM
    } else {
      MenuItem.SHOW_AS_ACTION_ALWAYS
    }
  }

  override suspend fun execAction(data: ActionData): Boolean {
    val activity = data.requireActivity() as? EditorHandlerActivity
    activity?.saveAll()
    return true
  }

  override fun postExec(data: ActionData, result: Any) {
    val activity = data.requireActivity() as? EditorHandlerActivity ?: return
    val editor = data.getEditor()
    val file = editor?.file

    if (file != null && file.exists() && file.canRead()) {
      openMarkdownPreview(activity, file)
    }
  }

  /**
   * Opens the Markdown preview tab with the given file.
   *
   * @param activity The EditorHandlerActivity
   * @param file The Markdown file to preview
   */
  private fun openMarkdownPreview(activity: EditorHandlerActivity, file: File) {
    val extension = file.extension.lowercase()
    val fragmentTabManager = activity.fragmentTabManager ?: return

    // Open the file tab using the fragment tab manager
    fragmentTabManager.openFileTab(file.absolutePath, extension)
  }
}
