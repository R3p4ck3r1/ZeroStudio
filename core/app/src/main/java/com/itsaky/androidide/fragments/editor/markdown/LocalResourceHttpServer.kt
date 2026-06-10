package com.itsaky.androidide.fragments.editor.markdown

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 单进程单实例的轻量 HTTP 服务，用于给 Markdown 预览 WebView 提供：
 *
 * 1. `/_assets/<path>` → 返回 APK 内的 [assets] 资源（highlight.js、github-markdown-css）
 * 2. `/<path>`        → 解析到当前 [baseDir] 下的本地文件，WebView 以 `http://127.0.0.1:<port>/` 为 base
 *                      后，相对路径资源（图片/视频/音频）会走本服务
 *
 * 设计要点：
 * - **进程级单例**：所有 MarkdownPreviewFragment 共用一个服务，避免反复起端口
 * - **端口 0**：由系统分配空闲端口，杜绝冲突
 * - **越狱防护**：`baseDir` 之外的 `..` / 绝对路径一律拒绝
 * - **同源**：所有资源来自 `http://127.0.0.1:<port>/`，与 WebView 加载的 HTML 同源
 *
 * @see MarkdownPreviewFragment
 */
object LocalResourceHttpServer {

  private val LOG = LoggerFactory.getLogger(LocalResourceHttpServer::class.java)
  private const val ASSETS_PREFIX = "/_assets/"

  @Volatile
  private var server: InternalServer? = null

  private val started = AtomicBoolean(false)

  /** 当前 HTTP 端口，未启动时为 -1。 */
  @Volatile
  var port: Int = -1
    private set

  /** WebView 应使用的 baseUrl，例如 `http://127.0.0.1:54321/`。 */
  val baseUrl: String
    get() = if (port > 0) "http://127.0.0.1:$port/" else ""

  /** 当前服务的根目录（用于相对路径资源解析）。 */
  @Volatile
  var baseDir: File? = null
    private set

  /**
   * 启动服务（如果尚未启动）。重复调用是幂等的。
   *
   * @return 实际监听的端口
   */
  @Synchronized
  fun start(appContext: Context, baseDir: File): Int {
    if (started.get() && server != null) {
      // 已启动：如果 baseDir 改变了，更新它；端口保持不变
      this.baseDir = baseDir
      return port
    }

    val ctx = appContext.applicationContext
    val s = InternalServer(ctx, baseDir).apply { start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
    // nanohttpd 2.x: getListeningPort() returns the actual bound port (0 = system assigned)
    val actualPort = s.listeningPort
    if (actualPort <= 0) {
      runCatching { s.closeAllConnections() }
      throw IllegalStateException("LocalResourceHttpServer failed to bind (port=$actualPort)")
    }
    server = s
    port = actualPort
    this.baseDir = baseDir
    started.set(true)
    LOG.info("LocalResourceHttpServer started on port {} (baseDir={})", actualPort, baseDir)
    return actualPort
  }

  /**
   * 停止服务。主要是测试/退出时调用，正常流程不需要。
   */
  @Synchronized
  fun stop() {
    if (!started.get()) return
    runCatching { server?.closeAllConnections() }
    server = null
    port = -1
    baseDir = null
    started.set(false)
    LOG.info("LocalResourceHttpServer stopped")
  }

  private class InternalServer(
    private val appContext: Context,
    initialBaseDir: File
  ) : NanoHTTPD(0) {

    @Volatile
    var currentBaseDir: File = initialBaseDir

    override fun serve(session: IHTTPSession): Response {
      val uri = session.uri
      return try {
        if (uri.startsWith(ASSETS_PREFIX)) {
          serveAsset(uri.substring(ASSETS_PREFIX.length))
        } else {
          serveFile(uri)
        }
      } catch (t: Throwable) {
        LOG.error("LocalResourceHttpServer failed to serve {}", uri, t)
        newFixedLengthResponse(
          Response.Status.INTERNAL_ERROR,
          "text/plain; charset=utf-8",
          "Internal error: ${t.javaClass.simpleName}: ${t.message ?: ""}"
        )
      }
    }

    private fun serveAsset(relativePath: String): Response {
      val cleaned = sanitizeRelativePath(relativePath)
        ?: return notFound("assets/$relativePath")
      return try {
        val stream = appContext.assets.open(cleaned)
        val mime = guessMime(cleaned)
        newChunkedResponse(Response.Status.OK, mime, stream)
      } catch (e: java.io.FileNotFoundException) {
        notFound("assets/$cleaned")
      }
    }

    private fun serveFile(uri: String): Response {
      val root = currentBaseDir
      if (root == null || !root.isDirectory) {
        return notFound("baseDir unavailable")
      }
      val decoded = java.net.URLDecoder.decode(uri, Charsets.UTF_8.name())
      val rel = decoded.trimStart('/')
      if (rel.isEmpty()) {
        return notFound("directory listing disabled")
      }
      val sanitized = sanitizeRelativePath(rel) ?: return forbidden("path traversal blocked")
      val target = File(root, sanitized).canonicalFile
      val rootCanonical = root.canonicalFile
      // 防止符号链接 / .. 越狱到 baseDir 之外
      if (!target.absolutePath.startsWith(rootCanonical.absolutePath + File.separator) &&
        target.absolutePath != rootCanonical.absolutePath
      ) {
        return forbidden("path outside baseDir")
      }
      if (!target.isFile || !target.canRead()) {
        return notFound(target.path)
      }
      val mime = guessMime(target.name)
      val input = FileInputStream(target)
      return newChunkedResponse(Response.Status.OK, mime, input)
    }

    private fun sanitizeRelativePath(path: String): String? {
      if (path.isBlank()) return null
      // 拒绝绝对路径、含盘符、含 NUL、含 ".."
      if (path.startsWith("/") || path.startsWith("\\")) return null
      if (path.contains('\u0000')) return null
      if (path.split('/', '\\').any { it == ".." }) return null
      return path
    }

    private fun guessMime(name: String): String {
      val byExt = URLConnection.guessContentTypeFromName(name)
      if (byExt != null) return byExt
      return when (name.substringAfterLast('.', "").lowercase()) {
        "js" -> "application/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "html", "htm" -> "text/html; charset=utf-8"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "md", "markdown" -> "text/markdown; charset=utf-8"
        "txt" -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
      }
    }

    private fun notFound(what: String): Response =
      newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "Not found: $what")

    private fun forbidden(why: String): Response =
      newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain; charset=utf-8", "Forbidden: $why")
  }
}
