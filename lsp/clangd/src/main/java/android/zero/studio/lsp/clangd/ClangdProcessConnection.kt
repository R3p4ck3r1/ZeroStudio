/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package android.zero.studio.lsp.clangd

import com.itsaky.androidide.utils.Environment
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.slf4j.LoggerFactory

/**
 * Clangd 本地进程连接管理器。
 *
 * 用途与功能：
 * 代替使用 JNI 和 UNIX Socket，这是一种更纯粹且符合标准 LSP 客户端规范的实现。
 * 通过 [ProcessBuilder] 直接在受限的 AndroidIDE 环境中拉起 NDK 提供的 clangd，
 * 并接管其 Stdio (Standard Input/Output) 以供 JSON-RPC 读写。
 *
 * @author android_zero
 */
class ClangdProcessConnection(
    private val workingDir: File,
    private val clangdExecutable: File,
    private val clangdArgs: List<String>
) : AutoCloseable {

    companion object {
        private val log = LoggerFactory.getLogger(ClangdProcessConnection::class.java)
    }

    private var process: Process? = null
    private var isClosed = false

    /** 供外部 JSON-RPC 读取 Clangd 推送消息的输入流 */
    val inputStream: InputStream
        get() = process?.inputStream ?: throw IllegalStateException("Process is not started")

    /** 供外部 JSON-RPC 向 Clangd 发送指令的输出流 */
    val outputStream: OutputStream
        get() = process?.outputStream ?: throw IllegalStateException("Process is not started")

    /**
     * 启动 Clangd 进程并建立连接。
     *
     * @throws IOException 当进程启动失败时抛出。
     */
    @Throws(IOException::class)
    fun start() {
        if (process != null) return
        isClosed = false

        val commandList = mutableListOf<String>().apply {
            add(clangdExecutable.absolutePath)
            addAll(clangdArgs)
        }

        log.info("Starting Clangd Process: {}", commandList.joinToString(" "))

        val pb = ProcessBuilder(commandList)
        pb.directory(workingDir)
        pb.redirectErrorStream(true) // 将 stderr 合并到 stdout 以便于统一日志捕获，或视情况分离

        // 注入必要的环境变量，确保 Clangd 能正确定位自身的内置头文件和库
        val env = pb.environment()
        Environment.putEnvironment(env, false)

        try {
            process = pb.start()
            log.info("Clangd Process started successfully (PID: {} - Note: Requires API 24+ for true PID)", getPidFallback(process))
        } catch (e: IOException) {
            log.error("Failed to start Clangd process at ${clangdExecutable.absolutePath}", e)
            throw e
        }
    }

    /**
     * 安全地关闭进程和释放流资源。
     */
    override fun close() {
        if (isClosed) return
        isClosed = true
        
        log.info("Shutting down Clangd Process...")
        try {
            process?.outputStream?.close()
            process?.inputStream?.close()
        } catch (e: Exception) {
            log.warn("Error closing Clangd streams", e)
        } finally {
            process?.destroy()
            process = null
        }
    }

    /** 获取进程状态以供监控 */
    fun isAlive(): Boolean {
        return process != null && !isClosed
    }

    private fun getPidFallback(p: Process?): String {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                p?.pid()?.toString() ?: "Unknown"
            } else {
                "Fallback-Legacy"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}