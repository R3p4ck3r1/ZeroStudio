package com.itsaky.androidide.fragments.editor.markdown

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Markdown 预览页面的状态机。
 *
 * - [filePath] 优先：磁盘上的 `.md` 文件路径
 * - [inlineContent] 次之：调用方直接传入的 Markdown 文本（不持久化，相对路径资源不可加载）
 *
 * 状态：
 * - [MarkdownPreviewState.Loading]  加载中
 * - [MarkdownPreviewState.Loaded]   渲染完成，携带 `html` 和 `baseUrl`
 * - [MarkdownPreviewState.Error]    加载/渲染失败，携带用户可见的错误信息
 *
 * **不**在 [onCleared] 关闭 [LocalResourceHttpServer]，进程内所有 preview tab 共用。
 */
class MarkdownPreviewViewModel(application: Application) : AndroidViewModel(application) {

  private val LOG = LoggerFactory.getLogger(MarkdownPreviewViewModel::class.java)

  private val _state = MutableLiveData<MarkdownPreviewState>(MarkdownPreviewState.Loading)
  val state: androidx.lifecycle.LiveData<MarkdownPreviewState> = _state

  private var job: Job? = null
  private var lastFilePath: String? = null
  private var lastInline: String? = null
  private var renderer: MarkdownRender? = null

  /**
   * 启动一次渲染。重复调用会取消上一次的协程。
   */
  fun load(filePath: String?, inlineContent: String?) {
    if (filePath == lastFilePath && inlineContent == lastInline && _state.value is MarkdownPreviewState.Loaded) {
      return
    }
    lastFilePath = filePath
    lastInline = inlineContent
    job?.cancel()
    _state.value = MarkdownPreviewState.Loading

    val app = getApplication<Application>()
    job = viewModelScope.launch {
      try {
        val result = withContext(Dispatchers.IO) {
          loadAndRender(app, filePath, inlineContent)
        }
        _state.value = result
      } catch (ce: CancellationException) {
        throw ce
      } catch (t: Throwable) {
        LOG.error("Failed to load markdown preview (filePath={})", filePath, t)
        _state.value = MarkdownPreviewState.Error(t.message ?: "Unable to load Markdown file.")
      }
    }
  }

  private fun loadAndRender(
    app: Application,
    filePath: String?,
    inlineContent: String?
  ): MarkdownPreviewState {
    val markdown: String
    val baseDir: File?

    if (!inlineContent.isNullOrBlank()) {
      markdown = inlineContent
      baseDir = null
    } else if (!filePath.isNullOrBlank()) {
      val f = File(filePath)
      if (!f.exists()) {
        return MarkdownPreviewState.Error("Markdown file does not exist:\n$filePath")
      }
      if (!f.canRead()) {
        return MarkdownPreviewState.Error("Markdown file is not readable:\n$filePath")
      }
      markdown = runCatching { f.readText(Charsets.UTF_8) }
        .getOrElse { return MarkdownPreviewState.Error(it.message ?: "Unable to read Markdown file.") }
      baseDir = f.parentFile
    } else {
      return MarkdownPreviewState.Error("No Markdown file or content was provided.")
    }

    // 启动 / 复用本地资源服务
    val port = if (baseDir != null) {
      runCatching { LocalResourceHttpServer.start(app, baseDir) }
        .getOrElse {
          LOG.warn("LocalResourceHttpServer start failed, continuing with absolute paths only", it)
          -1
        }
    } else -1

    val r = renderer ?: MarkdownRender(app.applicationContext).also { renderer = it }
    val body = runCatching { r.renderBody(markdown) }
      .getOrElse {
        return MarkdownPreviewState.Error("Failed to render Markdown: ${it.message ?: it.javaClass.simpleName}")
      }

    val theme = currentTheme()
    val html = MarkdownPageTemplate.wrap(body, theme)
    val baseUrl = if (port > 0) "http://127.0.0.1:$port/" else ""
    return MarkdownPreviewState.Loaded(html = html, baseUrl = baseUrl)
  }

  private fun currentTheme(): String {
    return when (AppCompatDelegate.getDefaultNightMode()) {
      AppCompatDelegate.MODE_NIGHT_YES -> "dark"
      AppCompatDelegate.MODE_NIGHT_NO -> "light"
      else -> "" // 跟随 prefers-color-scheme
    }
  }

  override fun onCleared() {
    super.onCleared()
    job?.cancel()
    // 故意不关 LocalResourceHttpServer，让所有 tab 共用
  }
}

/**
 * 预览页状态。
 */
sealed class MarkdownPreviewState {
  data object Loading : MarkdownPreviewState()
  data class Loaded(val html: String, val baseUrl: String) : MarkdownPreviewState()
  data class Error(val message: String) : MarkdownPreviewState()
}
