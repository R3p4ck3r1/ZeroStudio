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
 * 具有 Termux 终端沙盒环境变量注入能力的进程连接器。
 *
 * 功能与用途：
 * 包装 `ProcessBuilder`，但在启动进程前，严格按照 AndroidIDE/Termux 的底层规范
 * 通过 [Environment.putEnvironment] 注入必需的 Linux 环境变量（如 PATH, LD_LIBRARY_PATH, SYSROOT）。
 * 这是确保在移动设备上运行原生的 `clangd` 二进制文件时不发生 `linker 找不到 so 库` 的核心关键。
 *
 * 工作机制：
 * [ClangdLanguageServer] 请求启动
 *   -> 组装命令行并注入 AndroidIDE 的 Termux 环境变量
 *   -> 暴露底层的 InputStream 和 OutputStream 给 JSON-RPC 引擎。
 *   -> 单独起线程消化 stderr 避免进程阻塞。
 *
 * @author android_zero
 */
class TermuxClangdConnection(
    private val workingDir: File,
    private val clangdExecutable: File,
    private val clangdArgs: List<String>
) : AutoCloseable {

    companion object {
        private val log = LoggerFactory.getLogger(TermuxClangdConnection::class.java)
    }

    private var process: Process? = null
    private var isClosed = false
    private var stderrThread: Thread? = null

    val inputStream: InputStream
        get() = process?.inputStream ?: throw IllegalStateException("Process is not running")

    val outputStream: OutputStream
        get() = process?.outputStream ?: throw IllegalStateException("Process is not running")

    @Throws(IOException::class)
    fun start() {
        if (process != null) return
        isClosed = false

        val command = mutableListOf(clangdExecutable.absolutePath).apply {
            addAll(clangdArgs)
        }

        log.info("Bootstrapping Termux Clangd Connection: {}", command.joinToString(" "))

        val pb = ProcessBuilder(command)
        pb.directory(workingDir)
        
        // 关键点：不要 redirectErrorStream(true)，LSP 协议规定 Stdio 通道只能走合法的 JSON-RPC 报文。
        // 如果把 clangd 的错误日志（普通文本）混入 stdout，会导致 JsonRpcMessenger 解析 JSON 崩溃！
        pb.redirectErrorStream(false)

        // 注入 Termux 相关的沙盒环境变量，使得链接器(linker)能够找到对应的 libc++_shared.so 等依赖
        val envMap = pb.environment()
        Environment.putEnvironment(envMap, false)
        
        // 确保系统的 PATH 包含我们 NDK 工具链的目录
        val currentPath = envMap["PATH"] ?: ""
        envMap["PATH"] = "${clangdExecutable.parentFile.absolutePath}:$currentPath"

        try {
            process = pb.start()
            log.info("Termux Clangd process spawned successfully.")

            // 开辟独立的守护线程消费标准错误流 (stderr)，避免管道塞满导致进程死锁
            stderrThread = Thread {
                try {
                    process?.errorStream?.bufferedReader()?.useLines { lines ->
                        for (line in lines) {
                            if (isClosed) break
                            log.debug("[Clangd Stderr] {}", line)
                        }
                    }
                } catch (e: Exception) {
                    if (!isClosed) log.warn("Clangd stderr reader crashed", e)
                }
            }.apply {
                name = "Clangd-Stderr-Reader"
                isDaemon = true
                start()
            }

        } catch (e: IOException) {
            log.error("Failed to spawn Termux Clangd process", e)
            throw e
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        log.info("Destroying Termux Clangd Connection...")

        try {
            process?.outputStream?.close()
            process?.inputStream?.close()
            process?.errorStream?.close()
        } catch (e: Exception) {
            // Ignore
        } finally {
            stderrThread?.interrupt()
            process?.destroy()
            process = null
        }
    }
}