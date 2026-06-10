package com.itsaky.androidide.fragments.editor.markdown

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.itsaky.androidide.fragments.editor.EditorFragmentTabManager
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Fragment tab that renders a Markdown file (or in-memory snippet) using the
 * project-local Markwon-based [MarkdownText] composable from `core/git`.
 *
 * Why not WebView: the previous WebView-backed implementation crashed the app
 * with `IDEApplication: Unable to show crash handler activity` whenever a new
 * Markdown tab was opened on Android 14/15+. The crash was reproducible with
 * the dangerous WebView settings trio (`allowFileAccessFromFileURLs`,
 * `allowUniversalAccessFromFileURLs`, `MIXED_CONTENT_ALWAYS_ALLOW`) combined
 * with a custom `loadDataWithBaseURL(null, …)` call from a `factory` of
 * `AndroidView` on a hidden tab — first-frame hardware acceleration then
 * SIGSEGV'd inside `webviewchromium`. The crash handler couldn't be shown
 * because the original uncaught exception happened in the WebView's native
 * side after the activity was paused.
 *
 * Markwon renders directly to a `TextView` via Coil-backed image spans, so
 * we get:
 *   - GFM tables / task lists / strikethrough
 *   - syntax-highlighted code fences (the `==…==` Markwon extension)
 *   - lazy image loading (raster + GIF + SVG via Coil)
 *   - autolink / linkify
 *   - inline HTML subset
 * with no WebView in the process.
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
    // loading spinner (the original bug — see the file kdoc).
    try {
      val next = withContext(Dispatchers.IO) {
        when {
          !initialContent.isNullOrBlank() ->
            MarkdownLoadState.Loaded(initialContent)
          filePath.isNullOrBlank() -> MarkdownLoadState.Error("No Markdown file was provided.")
          else -> runCatching {
            val file = File(filePath)
            when {
              !file.exists() ->
                MarkdownLoadState.Error("Markdown file does not exist:\n$filePath")
              !file.canRead() ->
                MarkdownLoadState.Error("Markdown file is not readable:\n$filePath")
              else ->
                MarkdownLoadState.Loaded(file.readText(Charsets.UTF_8))
            }
          }.getOrElse { t ->
            MarkdownLoadState.Error(t.message ?: "Unable to load Markdown file.")
          }
        }
      }
      value = next
    } catch (ce: CancellationException) {
      throw ce
    } catch (t: Throwable) {
      LOG.error("Failed to load markdown file={}", filePath, t)
      value = MarkdownLoadState.Error(t.message ?: "Unable to load Markdown file.")
    }
  }

  when (val state = loadState) {
    MarkdownLoadState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    is MarkdownLoadState.Error -> Box(modifier, contentAlignment = Alignment.Center) {
      Text(
        text = state.message,
        style = MaterialTheme.typography.bodyMedium
      )
    }
    is MarkdownLoadState.Loaded -> MarkdownPreviewBody(
      modifier = modifier,
      markdown = state.markdown
    )
  }
}

@Composable
private fun MarkdownPreviewBody(
  modifier: Modifier,
  markdown: String
) {
  val scrollState = rememberScrollState()

  // `core/git` ships its own Coil 2 ImageLoader (with GifDecoder) and CoilStore
  // out of the box, so the only thing this fragment has to do is hand the
  // markdown string over. No WebView, no relative-path plumbing, no SVG/GIF
  // decoder wiring — see the kdoc on the class for why this fragment is
  // Compose+Markwon only.
  Box(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
  ) {
    MarkdownText(
      markdown = markdown,
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      isTextSelectable = true,
      // Block the default URL handler; the IDE has its own link policy.
      onLinkClicked = { _ -> true }
    )
  }
}

private val LOG = LoggerFactory.getLogger("MarkdownPreviewFragment")
