package android.zero.studio.lsp.clangd

import com.itsaky.androidide.utils.Environment
import java.io.File
import java.io.InputStream
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
    stream.bufferedReader().useLines { lines -> lines.forEach { log.warn("clangd: {}", it) } }
  }

  override fun close() {
    runCatching { process?.outputStream?.close() }
    runCatching { process?.inputStream?.close() }
    process?.destroy()
    runCatching { stderrThread?.interrupt() }
    process = null
  }
}
