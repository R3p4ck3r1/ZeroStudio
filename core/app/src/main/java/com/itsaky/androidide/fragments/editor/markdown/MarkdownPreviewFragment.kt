package com.itsaky.androidide.fragments.editor.markdown

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    return ComposeView(requireContext()).apply {
      setContent {
        MarkdownPreviewView(
        modifier = Modifier.fillMaxSize(),
        markdownContent = markdownContent ?: loadMarkdownContent(),
        onLinkClick = { url -> handleLinkClick(url) },
        onImageClick = { uri -> handleImageClick(uri) },
        onError = { error -> handleError(error) }
      )
      }
    }
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

  private fun handleLinkClick(url: String) {
    try {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
      startActivity(intent)
    } catch (e: Exception) {
      handleError("Failed to open link: $url")
    }
  }

  private fun handleImageClick(uri: Uri) {
    // Could implement image viewer dialog here
  }

  private fun handleError(error: String) {
    // Could show error toast or dialog here
  }

  companion object {
    const val ARG_MARKDOWN_CONTENT = "markdown_content"

    /**
     * Creates a new instance of MarkdownPreviewFragment with the given file path.
     *
     * @param filePath The path of the file to open
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

/**
 * Composable view for rendering Markdown content.
 *
 * @param modifier The modifier for this composable
 * @param markdownContent The Markdown content to render
 * @param onLinkClick Callback when a link is clicked
 * @param onImageClick Callback when an image is clicked
 * @param onError Callback when an error occurs
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownPreviewView(
  modifier: Modifier = Modifier,
  markdownContent: String?,
  onLinkClick: (String) -> Unit,
  onImageClick: (Uri) -> Unit,
  onError: (String) -> Unit
) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  // State to track if we should use WebView for HTML/JS/CSS rendering
  var useWebView by remember { mutableStateOf(false) }
  var webViewContent by remember { mutableStateOf<String?>(null) }

  // Check if content contains HTML/JS/CSS that needs WebView rendering
  LaunchedEffect(markdownContent) {
    if (markdownContent != null) {
      val containsHtml = markdownContent.contains("<html", ignoreCase = true) ||
        markdownContent.contains("<script", ignoreCase = true) ||
        markdownContent.contains("<style", ignoreCase = true)
      if (containsHtml) {
        useWebView = true
        webViewContent = convertMarkdownToHtml(markdownContent)
      }
    }
  }

  if (useWebView && webViewContent != null) {
    // Use WebView for HTML/JS/CSS embedded content
    WebViewMarkdownView(
      modifier = modifier,
      htmlContent = webViewContent!!,
      onLinkClick = onLinkClick,
      onError = onError
    )
  } else {
    // Use Markwon for standard Markdown rendering
    StandardMarkdownView(
      modifier = modifier,
      markdownContent = markdownContent,
      onLinkClick = onLinkClick,
      onImageClick = onImageClick,
      onError = onError,
      scrollState = scrollState
    )
  }
}

/**
 * Standard Markdown view using Markwon.
 */
@Composable
fun StandardMarkdownView(
  modifier: Modifier = Modifier,
  markdownContent: String?,
  onLinkClick: (String) -> Unit,
  onImageClick: (Uri) -> Unit,
  onError: (String) -> Unit,
  scrollState: androidx.compose.foundation.ScrollState
) {
  val context = LocalContext.current

  AndroidView(
    modifier = modifier.verticalScroll(scrollState),
    factory = { ctx ->
      val markwon = Markwon.builder(ctx)
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(TaskListPlugin.create(ctx))
        .usePlugin(HtmlPlugin.create())
        .usePlugin(CoilImagesPlugin.create(ctx))
        .build()

      android.widget.TextView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(32, 16, 32, 16)
        textSize = 16f
        setTextColor(android.graphics.Color.BLACK)
        markwon.setMarkdown(this, markdownContent ?: "")
      }
    },
    update = { textView ->
      if (markdownContent != null) {
        val markwon = Markwon.builder(context)
          .usePlugin(LinkifyPlugin.create())
          .usePlugin(TaskListPlugin.create(context))
          .usePlugin(HtmlPlugin.create())
          .usePlugin(CoilImagesPlugin.create(context))
          .build()
        markwon.setMarkdown(textView, markdownContent)
      }
    }
  )
}

/**
 * WebView-based Markdown view for HTML/JS/CSS embedded content.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewMarkdownView(
  modifier: Modifier = Modifier,
  htmlContent: String,
  onLinkClick: (String) -> Unit,
  onError: (String) -> Unit
) {
  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      WebView(ctx).apply {
        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          allowFileAccess = true
          allowContentAccess = true
          loadWithOverviewMode = true
          useWideViewPort = true
          builtInZoomControls = true
          displayZoomControls = false
          setSupportZoom(true)
          mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
          cacheMode = WebSettings.LOAD_DEFAULT
        }

        webViewClient = object : WebViewClient() {
          override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
          }

          override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            url?.let { onLinkClick(it) }
            return true
          }
        }

        webChromeClient = WebChromeClient()

        loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
      }
    },
    update = { webView ->
      webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }
  )
}

/**
 * Converts Markdown content to HTML for WebView rendering.
 * This handles embedded HTML/JS/CSS content.
 *
 * @param markdown The Markdown content to convert
 * @return HTML string ready for WebView rendering
 */
private fun convertMarkdownToHtml(markdown: String): String {
  // If the content is already HTML, return it wrapped properly
  if (markdown.contains("<html", ignoreCase = true)) {
    return markdown
  }

  // Basic Markdown to HTML conversion for embedded content
  return """
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <style>
        body {
          font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
          line-height: 1.6;
          padding: 16px;
          max-width: 100%;
          overflow-x: auto;
        }
        pre, code {
          background-color: #f4f4f4;
          border-radius: 4px;
          padding: 2px 6px;
          overflow-x: auto;
        }
        pre {
          padding: 16px;
        }
        img {
          max-width: 100%;
          height: auto;
        }
        video, audio {
          max-width: 100%;
        }
      </style>
    </head>
    <body>
      $markdown
    </body>
    </html>
  """.trimIndent()
}
