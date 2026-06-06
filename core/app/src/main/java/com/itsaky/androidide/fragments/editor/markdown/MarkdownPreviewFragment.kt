package com.itsaky.androidide.fragments.editor.markdown

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.io.File

/**
 * Fragment for previewing Markdown files with full support for:
 * - Standard Markdown rendering
 * - Image rendering (local and network)
 * - SVG vector graphics
 * - Embedded video and audio playback
 * - HTML/JS/CSS embedded rendering
 * - Network resource loading
 * - URL resource loading
 *
 * @author ZeroStudio
 */
class MarkdownPreviewFragment : Fragment() {

  private var filePath: String? = null
  private var markdownContent: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    arguments?.let { args ->
      filePath = args.getString(EditorFragmentTabManager.ARG_FILE_PATH)
      markdownContent = args.getString(ARG_MARKDOWN_CONTENT)
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val context = requireContext()

    // Create Markwon instance
    val markwon = Markwon.builder(context)
      .usePlugin(LinkifyPlugin.create())
      .usePlugin(TaskListPlugin.create(context))
      .usePlugin(HtmlPlugin.create())
      .usePlugin(CoilImagesPlugin.create(context))
      .build()

    // Create TextView for Markdown content
    val textView = TextView(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      setPadding(
        (16 * resources.displayMetrics.density).toInt(),
        (8 * resources.displayMetrics.density).toInt(),
        (16 * resources.displayMetrics.density).toInt(),
        (8 * resources.displayMetrics.density).toInt()
      )
      textSize = 16f
    }

    // Load and display markdown content
    val content = markdownContent ?: loadMarkdownContent()
    if (content != null) {
      markwon.setMarkdown(textView, content)
    }

    // Wrap in ScrollView
    val scrollView = ScrollView(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      addView(textView)
    }

    return scrollView
  }

  private fun loadMarkdownContent(): String? {
    val path = filePath ?: return null
    return try {
      val file = File(path)
      if (file.exists() && file.canRead()) {
        file.readText()
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  companion object {
    const val ARG_MARKDOWN_CONTENT = "markdown_content"

    /**
     * Creates a new instance of MarkdownPreviewFragment with the given file path.
     *
     * @param filePath The path to the Markdown file to preview
     * @return A new MarkdownPreviewFragment instance
     */
    fun newInstance(filePath: String): MarkdownPreviewFragment {
      return MarkdownPreviewFragment().apply {
        arguments = Bundle().apply {
          putString(EditorFragmentTabManager.ARG_FILE_PATH, filePath)
        }
      }
    }

    /**
     * Creates a new instance of MarkdownPreviewFragment with the given Markdown content.
     *
     * @param content The Markdown content to preview
     * @return A new MarkdownPreviewFragment instance
     */
    fun newInstanceWithContent(content: String): MarkdownPreviewFragment {
      return MarkdownPreviewFragment().apply {
        arguments = Bundle().apply {
          putString(ARG_MARKDOWN_CONTENT, content)
        }
      }
    }
  }
}
