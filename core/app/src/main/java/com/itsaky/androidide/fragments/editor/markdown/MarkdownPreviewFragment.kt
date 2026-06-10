package com.itsaky.androidide.fragments.editor.markdown

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
  }

  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: android.view.ViewGroup?,
    savedInstanceState: Bundle?
  ): android.view.View {
    return androidx.compose.ui.platform.ComposeView(requireContext()).apply {
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
  val loadState by produceState<MarkdownLoadState>(MarkdownLoadState.Loading, initialContent, filePath) {
    value = withContext(Dispatchers.IO) {
      when {
        initialContent != null -> MarkdownLoadState.Loaded(initialContent)
        filePath.isNullOrBlank() -> MarkdownLoadState.Error("No Markdown file was provided.")
        else -> runCatching {
          val file = File(filePath)
          when {
            !file.exists() -> MarkdownLoadState.Error("Markdown file does not exist:\n$filePath")
            !file.canRead() -> MarkdownLoadState.Error("Markdown file is not readable:\n$filePath")
            else -> MarkdownLoadState.Loaded(file.readText(Charsets.UTF_8))
          }
        }.getOrElse { MarkdownLoadState.Error(it.message ?: "Unable to load Markdown file.") }
      }
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

          override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
        }
        webChromeClient = WebChromeClient()
        loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
      }
    },
    update = { it.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null) }
  )
}

private fun convertMarkdownToHtml(markdown: String, filePath: String?): String {
  val body = MarkdownHtmlRenderer(File(filePath ?: "").parentFile).render(markdown)
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

private class MarkdownHtmlRenderer(private val baseDir: File?) {
  fun render(markdown: String): String {
    val out = StringBuilder()
    var inCode = false
    var codeLang = ""
    val paragraph = StringBuilder()

    fun flushParagraph() {
      if (paragraph.isNotBlank()) {
        out.append("<p>").append(renderInline(paragraph.toString().trim())).append("</p>\n")
        paragraph.clear()
      }
    }

    markdown.replace("\r\n", "\n").lines().forEach { raw ->
      val line = raw.trimEnd()
      if (line.trimStart().startsWith("```")) {
        if (inCode) {
          out.append("</code></pre>\n")
          inCode = false
        } else {
          flushParagraph()
          codeLang = line.trim().removePrefix("```").trim()
          out.append("<pre><code")
          if (codeLang.isNotBlank()) out.append(" class=\"language-").append(escapeAttr(codeLang)).append("\"")
          out.append(">")
          inCode = true
        }
        return@forEach
      }
      if (inCode) {
        out.append(escapeHtml(raw)).append('\n')
        return@forEach
      }
      if (line.isBlank()) {
        flushParagraph()
        return@forEach
      }
      val trimmed = line.trimStart()
      val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed)
      if (heading != null) {
        flushParagraph()
        val level = heading.groupValues[1].length
        out.append("<h").append(level).append('>').append(renderInline(heading.groupValues[2]))
          .append("</h").append(level).append(">\n")
      } else if (trimmed.startsWith(">")) {
        flushParagraph()
        out.append("<blockquote>").append(renderInline(trimmed.removePrefix(">").trim())).append("</blockquote>\n")
      } else if (Regex("^[-*+]\\s+.+").matches(trimmed)) {
        flushParagraph()
        out.append("<ul><li>").append(renderInline(trimmed.substring(2).trim())).append("</li></ul>\n")
      } else {
        if (paragraph.isNotEmpty()) paragraph.append('\n')
        paragraph.append(line)
      }
    }
    if (inCode) out.append("</code></pre>\n")
    flushParagraph()
    return out.toString()
  }

  private fun renderInline(text: String): String {
    var html = escapeHtml(text)
    html = Regex("!\\[([^]]*)]\\(([^)\\s]+)(?:\\s+\"([^\"]*)\")?\\)").replace(html) { m ->
      val alt = m.groupValues[1]
      val src = resolveResource(m.groupValues[2])
      val title = m.groupValues.getOrNull(3).orEmpty()
      val lower = src.substringBefore('?').lowercase()
      when {
        lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov") ->
          "<video controls src=\"${escapeAttr(src)}\" title=\"${escapeAttr(title.ifBlank { alt })}\"></video>"
        lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".m4a") ->
          "<audio controls src=\"${escapeAttr(src)}\"></audio>"
        else -> "<img src=\"${escapeAttr(src)}\" alt=\"${escapeAttr(alt)}\" title=\"${escapeAttr(title)}\"/>"
      }
    }
    html = Regex("\\[([^]]+)]\\(([^)\\s]+)(?:\\s+\"([^\"]*)\")?\\)").replace(html) { m ->
      val href = resolveResource(m.groupValues[2])
      "<a href=\"${escapeAttr(href)}\">${m.groupValues[1]}</a>"
    }
    html = Regex("`([^`]+)`").replace(html) { "<code>${it.groupValues[1]}</code>" }
    html = Regex("\\*\\*([^*]+)\\*\\*").replace(html) { "<strong>${it.groupValues[1]}</strong>" }
    html = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)").replace(html) { "<em>${it.groupValues[1]}</em>" }
    return html.replace("\n", "<br/>")
  }

  private fun resolveResource(resource: String): String {
    if (resource.startsWith("http://") || resource.startsWith("https://") || resource.startsWith("data:") || resource.startsWith("file:")) {
      return resource
    }
    return runCatching { Uri.fromFile(File(baseDir, resource)).toString() }.getOrDefault(resource)
  }
}

private fun CharSequence.isNotBlank(): Boolean = this.toString().isNotBlank()
private fun escapeHtml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
private fun escapeAttr(value: String): String = escapeHtml(value).replace("\"", "&quot;")
