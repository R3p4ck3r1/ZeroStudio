package android.zero.studio.lsp.clangd

import com.itsaky.androidide.utils.Environment
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import org.slf4j.LoggerFactory

class TermuxClangdConnection(
    private val workingDir: File,
    private val toolchain: NdkToolchainLocator.Toolchain,
    private val arguments: List<String>,
) : AutoCloseable {
  private val log = LoggerFactory.getLogger(javaClass)
  private var process: Process? = null
  private var stderrThread: Thread? = null

  val inputStream: InputStream get() = requireNotNull(process) { "clangd has not been started" }.inputStream
  val outputStream: OutputStream get() = requireNotNull(process) { "clangd has not been started" }.outputStream

  fun start() {
    if (process != null) return
    val command = listOf(toolchain.clangd.absolutePath) + arguments
    val builder = ProcessBuilder(command).directory(workingDir).redirectErrorStream(false)
    val env = builder.environment()
    Environment.putEnvironment(env, false)
    env["ANDROID_NDK_HOME"] = toolchain.ndkRoot.absolutePath
    env["ANDROID_NDK_ROOT"] = toolchain.ndkRoot.absolutePath
    env["ANDROID_NDK"] = toolchain.ndkRoot.absolutePath
    env["NDK_HOME"] = toolchain.ndkRoot.absolutePath
    env["PATH"] = toolchain.binDir.absolutePath + File.pathSeparator + (env["PATH"] ?: "")
    env["CLANGD_NDK_VERSION"] = toolchain.ndkVersion
    env["CLANGD_TOOLCHAIN_BIN"] = toolchain.binDir.absolutePath
    process = builder.start()
    stderrThread = Thread({ drainStderr() }, "clangd-stderr-${toolchain.ndkVersion}").apply {
      isDaemon = true
      start()
    }
    log.info("Started clangd from {} for NDK {}", toolchain.clangd.absolutePath, toolchain.ndkVersion)
  }

  private fun drainStderr() {
    val stream = process?.errorStream ?: return
    try {
      stream.bufferedReader().useLines { lines -> lines.forEach { log.warn("clangd: {}", it) } }
    } catch (error: InterruptedIOException) {
      // Expected when close() is called while stderr thread is blocked on read.
      log.debug("clangd stderr reader interrupted while closing process")
    } catch (error: java.io.IOException) {
      if (process != null) {
        log.warn("Failed reading clangd stderr", error)
      } else {
        log.debug("clangd stderr stream closed while shutting down")
      }
    }
  }

  override fun close() {
    process = process ?: return
    runCatching { process?.outputStream?.close() }
    runCatching { process?.inputStream?.close() }
    runCatching { process?.errorStream?.close() }
    process?.destroy()
    runCatching { stderrThread?.interrupt() }
    process = null
    stderrThread = null
  }
}
