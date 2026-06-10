package com.itsaky.androidide.fragments.editor.markdown

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.slf4j.LoggerFactory

/** Fragment tab that renders Markdown from a file path or in-memory content. */
class MarkdownPreviewFragment : Fragment() {

  private var filePath: String? = null
  private var markdownContent: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    arguments?.let { args ->
      filePath = args.getString(EditorFragmentTabManager.ARG_FILE_PATH)
      markdownContent = args.getString(ARG_MARKDOWN_CONTENT)
    }
    LOG.debug(
      "onCreate: filePath={}, hasContent={}",
      filePath,
      !markdownContent.isNullOrBlank()
    )
  }

  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return ComposeView(requireContext()).apply {
      // Use the fragment's view lifecycle so the composition follows the
      // host tab's visibility (hidden tabs suspend, shown tabs resume) and
      // is torn down when the view is destroyed.
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MarkdownPreviewScreen(
          modifier = Modifier.fillMaxSize(),
          filePath = filePath,
          initialContent = markdownContent
        )
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    LOG.debug("onDestroyView: filePath={}", filePath)
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(MarkdownPreviewFragment::class.java)

    const val ARG_MARKDOWN_CONTENT = "markdown_content"

    val SUPPORTED_EXTENSIONS =
      setOf("md", "mdr", "markdown", "mdown", "mkd", "mkdn", "mdoc", "mdtext", "mdx")

    fun newInstance(filePath: String): MarkdownPreviewFragment {
      return MarkdownPreviewFragment().apply {
        arguments = Bundle().apply { putString(EditorFragmentTabManager.ARG_FILE_PATH, filePath) }
      }
    }

    fun newInstanceWithContent(content: String): MarkdownPreviewFragment {
      return MarkdownPreviewFragment().apply {
        arguments = Bundle().apply { putString(ARG_MARKDOWN_CONTENT, content) }
      }
    }
  }
}

private sealed interface MarkdownLoadState {
  data object Loading : MarkdownLoadState
  data class Loaded(val markdown: String) : MarkdownLoadState
  data class Error(val message: String) : MarkdownLoadState
}

@Composable
private fun MarkdownPreviewScreen(
  modifier: Modifier = Modifier,
  filePath: String?,
  initialContent: String?
) {
  val loadState by produceState<MarkdownLoadState>(
    initialValue = MarkdownLoadState.Loading,
    key1 = initialContent,
    key2 = filePath
  ) {
    // Any exception in the producer (including IO errors) must transition the
    // state machine to a terminal Error, otherwise the UI gets stuck on the
    // loading spinner when the SDK load path fails.
    try {
      val next = withContext(Dispatchers.IO) {
        when {
          !initialContent.isNullOrBlank() -> MarkdownLoadState.Loaded(initialContent)
          filePath.isNullOrBlank() -> MarkdownLoadState.Error("No Markdown file was provided.")
          else -> runCatching {
            val file = File(filePath)
            when {
              !file.exists() -> MarkdownLoadState.Error("Markdown file does not exist:\n$filePath")
              !file.canRead() -> MarkdownLoadState.Error("Markdown file is not readable:\n$filePath")
              else -> MarkdownLoadState.Loaded(file.readText(Charsets.UTF_8))
            }
          }.getOrElse { t ->
            MarkdownLoadState.Error(t.message ?: "Unable to load Markdown file.")
          }
        }
      }
      value = next
    } catch (ce: CancellationException) {
      // Cooperative cancellation: do not overwrite state, let composition handle it.
      throw ce
    } catch (t: Throwable) {
      LOG.error("Failed to load markdown file={}", filePath, t)
      value = MarkdownLoadState.Error(t.message ?: "Unable to load Markdown file.")
    }
  }

  when (val state = loadState) {
    MarkdownLoadState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    is MarkdownLoadState.Error -> Box(modifier, contentAlignment = Alignment.Center) { Text(state.message) }
    is MarkdownLoadState.Loaded -> {
      val htmlContent = remember(state.markdown, filePath) { convertMarkdownToHtml(state.markdown, filePath) }
      MarkdownWebView(modifier, filePath, htmlContent)
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MarkdownWebView(modifier: Modifier, filePath: String?, htmlContent: String) {
  val baseUrl = remember(filePath) { filePath?.let { File(it).parentFile?.toURI()?.toString() } }
  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      WebView(ctx).apply {
        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          allowFileAccess = true
          allowContentAccess = true
          allowFileAccessFromFileURLs = true
          allowUniversalAccessFromFileURLs = true
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

          override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
          }

          override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
        }
        webChromeClient = WebChromeClient()
        loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
      }
    },
    update = { it.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null) }
  )
}

/**
 * Renders the given Markdown source to a self-contained HTML document using
 * the JetBrains Markdown SDK (org.intellij.markdown) with the GitHub-flavoured
 * Markdown flavour. The HTML body is wrapped with a small stylesheet so it
 * renders correctly in the embedded [WebView].
 */
private fun convertMarkdownToHtml(markdown: String, filePath: String?): String {
  val body = runCatching {
    val flavour = GFMFlavourDescriptor(useSafeLinks = true)
    val parser = MarkdownParser(flavour)
    val tree = parser.buildMarkdownTreeFromString(markdown)
    HtmlGenerator(markdown, tree, flavour).generateHtml()
  }.getOrElse { t ->
    LOG.error("Markdown SDK render failed for {}", filePath, t)
    "<pre>${escapeHtml(t.message ?: "Markdown render failed.")}</pre>"
  }

  return """
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <style>
        :root { color-scheme: light dark; }
        body { margin: 0; padding: 16px; font-family: sans-serif; line-height: 1.55; }
        .markdown-body { max-width: 980px; margin: 0 auto; overflow-wrap: anywhere; }
        img, video, audio, iframe, svg { max-width: 100%; }
        video, audio { display: block; margin: 16px 0; }
        pre { padding: 12px; overflow: auto; border-radius: 8px; background: rgba(127,127,127,.14); }
        code { font-family: monospace; background: rgba(127,127,127,.14); padding: 0.1em 0.3em; border-radius: 4px; }
        pre code { background: transparent; padding: 0; }
        blockquote { margin-left: 0; padding-left: 1em; border-left: 4px solid #8a8a8a; color: #777; }
        table { border-collapse: collapse; display: block; overflow-x: auto; width: 100%; }
        th, td { border: 1px solid #8885; padding: 6px 10px; }
        a { color: #4f8cff; }
      </style>
    </head>
    <body><article class="markdown-body">$body</article></body>
    </html>
  """.trimIndent()
}

private val LOG = LoggerFactory.getLogger("MarkdownPreviewFragment")

private fun escapeHtml(value: String): String =
  value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
