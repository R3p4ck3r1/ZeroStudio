package com.itsaky.androidide.fragments.editor.markdown

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager

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

  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    arguments?.let { args ->
      filePath = args.getString(EditorFragmentTabManager.ARG_FILE_PATH)
      markdownContent = args.getString(ARG_MARKDOWN_CONTENT)
    }
  }

  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: android.view.ViewGroup?,
    savedInstanceState: android.os.Bundle?
  ): android.view.View {
    val context = requireContext()
    return ComposeView(context).apply {
      setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MarkdownPreviewScreen(
          modifier = Modifier.fillMaxSize(),
          filePath = filePath,
          initialContent = markdownContent
        )
      }
    }
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
        arguments = android.os.Bundle().apply {
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
        arguments = android.os.Bundle().apply {
          putString(ARG_MARKDOWN_CONTENT, content)
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownPreviewScreen(
  modifier: Modifier = Modifier,
  filePath: String?,
  initialContent: String?
) {
  val context = LocalContext.current

  // Load markdown content
  val content = remember(initialContent, filePath) {
    initialContent ?: filePath?.let { path ->
      try {
        val file = File(path)
        if (file.exists() && file.canRead()) {
          file.readText()
        } else {
          null
        }
      } catch (e: Exception) {
        null
      }
    } ?: "# No Content"
  }

  // Convert markdown to HTML for WebView
  val htmlContent = remember(content) {
    convertMarkdownToHtml(content, filePath)
  }

  // Render using WebView
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
          mediaPlaybackRequiresUserGesture = false
        }

        webViewClient = object : WebViewClient() {
          override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
          }
        }

        webChromeClient = WebChromeClient()

        loadDataWithBaseURL(
          filePath?.let { File(it).parentFile?.toURI()?.toString() },
          htmlContent,
          "text/html",
          "UTF-8",
          null
        )
      }
    },
    update = { webView ->
      webView.loadDataWithBaseURL(
        filePath?.let { File(it).parentFile?.toURI()?.toString() },
        htmlContent,
        "text/html",
        "UTF-8",
        null
      )
    }
  )
}

/**
 * Converts Markdown content to HTML for WebView rendering.
 * Uses a powerful JavaScript Markdown renderer for full feature support.
 *
 * @param markdown The Markdown content to convert
 * @param filePath Optional path to the Markdown file (for relative path resolution)
 * @return HTML string ready for WebView rendering
 */
private fun convertMarkdownToHtml(markdown: String, filePath: String?): String {
  // Escape special characters in markdown for use in JavaScript
  val escapedMarkdown = markdown
    .replace("\\", "\\\\")
    .replace("`", "\\`")
    .replace("$", "\\$")
    .replace("\n", "\\n")
    .replace("\"", "\\\"")
  
  return """
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Markdown Preview</title>
      
      <!-- GitHub Markdown CSS -->
      <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.2.0/github-markdown.min.css">
      
      <!-- Highlight.js for code syntax highlighting -->
      <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css">
      <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
      
      <!-- Marked.js for Markdown rendering -->
      <script src="https://cdnjs.cloudflare.com/ajax/libs/marked/11.1.1/marked.min.js"></script>
      
      <style>
        body {
          box-sizing: border-box;
          min-width: 200px;
          max-width: 980px;
          margin: 0 auto;
          padding: 20px;
        }
        
        .markdown-body {
          box-sizing: border-box;
          min-width: 200px;
          margin: 0 auto;
          padding: 45px;
        }
        
        @media (max-width: 767px) {
          .markdown-body {
            padding: 15px;
          }
        }
        
        /* Ensure images, videos, and audio responsive */
        .markdown-body img,
        .markdown-body video,
        .markdown-body audio {
          max-width: 100%;
          height: auto;
          display: block;
          margin: 16px 0;
        }
        
        /* Code block styling */
        .markdown-body pre {
          padding: 16px;
          overflow: auto;
          font-size: 85%;
          line-height: 1.45;
          background-color: #f6f8fa;
          border-radius: 6px;
        }
        
        .markdown-body pre code {
          background-color: transparent;
          border: 0;
          padding: 0;
        }
        
        /* Responsive tables */
        .markdown-body table {
          width: 100%;
          display: block;
          overflow-x: auto;
        }
      </style>
    </head>
    <body>
      <article class="markdown-body" id="content"></article>
      
      <script>
        // Configure marked options
        marked.setOptions({
          breaks: true,
          gfm: true,
          tables: true,
          highlight: function(code, lang) {
            if (lang && hljs.getLanguage(lang)) {
              try {
                return hljs.highlight(code, { language: lang }).value;
              } catch (e) {
                // If highlighting fails, just return plain code
              }
            }
            return code;
          }
        });
        
        // Render markdown
        const markdown = `${escapedMarkdown}`;
        const content = document.getElementById('content');
        content.innerHTML = marked.parse(markdown);
        
        // Apply syntax highlighting
        document.querySelectorAll('pre code').forEach((block) => {
          hljs.highlightElement(block);
        });
      </script>
    </body>
    </html>
  """.trimIndent()
}
