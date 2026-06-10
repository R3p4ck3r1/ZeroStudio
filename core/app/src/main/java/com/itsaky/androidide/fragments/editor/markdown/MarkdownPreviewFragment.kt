package com.itsaky.androidide.fragments.editor.markdown

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import org.slf4j.LoggerFactory

private val LOG = LoggerFactory.getLogger("MarkdownPreviewFragment")

/**
 * Markdown 预览 Fragment。
 *
 * 重写自 2026-06-10 的设计：使用 Markwon 4.6.2 + nanohttpd 本地资源服务 +
 * bundled highlight.js / github-markdown-css，在 WebView 中渲染。详细设计：
 * [docs/superpowers/specs/2026-06-10-markdown-preview-rich-rendering-design.md]
 *
 * 关键加固（应对 Android 12 Samsung SM-A217F 上的 WebViewChromium SIGSEGV）：
 *  - WebView 在 `AndroidView.factory` 内构造，**不**在 `onCreateView` 创建
 *  - `loadDataWithBaseURL` 在 view 已 attach 后才执行
 *  - 关闭 `allowFileAccessFromFileURLs` / `allowUniversalAccessFromFileURLs` / 混合内容
 *  - 构造异常被 `runCatching` 捕获，失败时显示只读 TextView
 *  - 走 LAYER_TYPE_SOFTWARE 作为最后兜底（无 WebView 时）
 */
class MarkdownPreviewFragment : Fragment() {

  companion object {
    const val ARG_MARKDOWN_CONTENT = "markdown_content"
    val SUPPORTED_EXTENSIONS = setOf("md", "mdr", "markdown", "mdown", "mkd", "mkdn", "mdoc", "mdtext", "mdx")

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

  private val viewModel: MarkdownPreviewViewModel by viewModels()
  private var filePath: String? = null
  private var inlineContent: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    arguments?.let { args ->
      filePath = args.getString(EditorFragmentTabManager.ARG_FILE_PATH)
      inlineContent = args.getString(ARG_MARKDOWN_CONTENT)
    }
    LOG.debug("onCreate: filePath={}, hasInlineContent={}", filePath, !inlineContent.isNullOrBlank())
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        MarkdownPreviewScreen(
          viewModel = viewModel,
          filePath = filePath,
          inlineContent = inlineContent,
          onFirstAttach = { fp, ic -> viewModel.load(fp, ic) }
        )
      }
    }
  }
}

/**
 * 顶层 Composable。订阅 [MarkdownPreviewViewModel] 状态，三态渲染。
 */
@Composable
private fun MarkdownPreviewScreen(
  viewModel: MarkdownPreviewViewModel,
  filePath: String?,
  inlineContent: String?,
  onFirstAttach: (String?, String?) -> Unit
) {
  // 第一次进入时触发 load
  LaunchedEffect(filePath, inlineContent) {
    onFirstAttach(filePath, inlineContent)
  }
  val state by viewModel.state.observeAsState(MarkdownPreviewState.Loading)

  when (val s = state) {
    is MarkdownPreviewState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    is MarkdownPreviewState.Error -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
      Text(s.message, style = MaterialTheme.typography.bodyMedium)
    }
    is MarkdownPreviewState.Loaded -> WebViewContainer(html = s.html, baseUrl = s.baseUrl)
  }
}

/**
 * WebView 容器。负责：
 *  - 在 `AndroidView.factory` 内构造 WebView（崩溃隔离）
 *  - 等 view attached 后再 loadDataWithBaseURL
 *  - 构造失败时降级为 TextView
 */
@Composable
private fun WebViewContainer(html: String, baseUrl: String) {
  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      try {
        createSafeWebView(ctx).also { wv ->
          wv.tag = PendingLoad(html, baseUrl)
        }
      } catch (t: Throwable) {
        LOG.error("Failed to create WebView; falling back to TextView", t)
        TextView(ctx).apply {
          text = "Preview is not available on this device.\n\n${t.javaClass.simpleName}: ${t.message ?: ""}"
          setPadding(32, 32, 32, 32)
        }
      }
    },
    update = { view ->
      if (view !is WebView) return@AndroidView
      // 总是把最新的内容写到 tag；如果和上次一样就跳过
      val prev = view.tag as? PendingLoad
      if (prev != null && prev.matches(html, baseUrl)) return@AndroidView
      view.tag = PendingLoad(html, baseUrl)
      if (view.isAttachedToWindow) {
        doLoad(view, view.tag as PendingLoad)
        view.tag = null
      } else {
        // 等 attach 后再加载；用一次性 listener 避免每次 update 都注册
        view.removeOnAttachStateChangeListener(PendingAttachListener)
        view.addOnAttachStateChangeListener(PendingAttachListener)
      }
    }
  )
}

private val PendingAttachListener = object : View.OnAttachStateChangeListener {
  override fun onViewAttachedToWindow(v: View) {
    v.removeOnAttachStateChangeListener(this)
    if (v is WebView) {
      val pending = v.tag as? PendingLoad ?: return
      v.tag = null
      doLoad(v, pending)
    }
  }

  override fun onViewDetachedFromWindow(v: View) {
    // ignore
  }
}

private data class PendingLoad(val html: String, val baseUrl: String) {
  fun matches(html: String, baseUrl: String) = this.html == html && this.baseUrl == baseUrl
}

@SuppressLint("SetJavaScriptEnabled")
private fun createSafeWebView(ctx: android.content.Context): WebView {
  return WebView(ctx).apply {
    settings.javaScriptEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.domStorageEnabled = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    // 不让 WebView 在被 detach 后自动销毁，由我们手动管理
    isFocusable = true
    isFocusableInTouchMode = true
  }
}

private fun doLoad(webView: WebView, pending: PendingLoad) {
  try {
    // baseUrl 为空字符串时，回退到 about:blank（inline 模式无相对路径资源）
    val base = if (pending.baseUrl.isBlank()) "about:blank" else pending.baseUrl
    webView.loadDataWithBaseURL(base, pending.html, "text/html", "utf-8", null)
  } catch (t: Throwable) {
    LOG.error("loadDataWithBaseURL failed; falling back to LAYER_TYPE_SOFTWARE", t)
    webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    runCatching { webView.loadDataWithBaseURL("about:blank", pending.html, "text/html", "utf-8", null) }
  }
}
