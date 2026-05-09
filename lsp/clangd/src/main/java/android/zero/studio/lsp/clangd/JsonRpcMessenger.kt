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

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

/**
 * 纯粹的 JSON-RPC 消息收发引擎。
 *
 * 用途与功能：
 * 负责在 Java 进程和 clangd 原生进程之间，通过标准输入/输出流 (Stdio) 实现符合
 * Language Server Protocol (LSP) 规范的报文序列化、反序列化与分发。
 * 
 * 工作流程线路图：
 * - 发送端：组装 JSON -> 添加 `Content-Length: ...\r\n\r\n` 请求头 -> 写入 `OutputStream`
 * - 接收端：轮询 `InputStream` -> 解析请求头 -> 提取定长 JSON -> 分发至 `CompletableFuture` 或 Notification 处理器。
 *
 * 上下文关系：
 * 附属于 [ClangdLanguageServer]，依赖 [ClangdProcessConnection] 提供的流。
 *
 * @author android_zero
 */
class JsonRpcMessenger(
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val notificationHandler: (String, JSONObject) -> Unit
) {

    companion object {
        private val log = LoggerFactory.getLogger(JsonRpcMessenger::class.java)
    }

    private val requestIdGenerator = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, CompletableFuture<JSONObject>>()
    
    @Volatile
    private var isRunning = true
    private var readerThread: Thread? = null

    /**
     * 启动异步接收守护线程。
     */
    fun start() {
        if (readerThread != null) return
        isRunning = true
        readerThread = thread(start = true, isDaemon = true, name = "Clangd-Rpc-Reader") {
            readerLoop()
        }
    }

    /**
     * 关闭通信引擎并释放资源。
     */
    fun shutdown() {
        isRunning = false
        readerThread?.interrupt()
        pendingRequests.values.forEach { it.cancel(true) }
        pendingRequests.clear()
    }

    /**
     * 发送 LSP Notification (无需等待响应的单向通知，如 didOpen, didChange)。
     * 
     * @param method LSP 方法名
     * @param params 载荷参数
     */
    fun sendNotification(method: String, params: JSONObject) {
        if (!isRunning) return
        val message = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        writeMessage(message.toString())
    }

    /**
     * 发送 LSP Request (双向请求，返回 CompletableFuture 供上层阻塞/挂起等待结果)。
     *
     * @param method LSP 方法名
     * @param params 载荷参数
     * @return 包含响应数据的 CompletableFuture
     */
    fun sendRequest(method: String, params: JSONObject): CompletableFuture<JSONObject> {
        val future = CompletableFuture<JSONObject>()
        if (!isRunning) {
            future.completeExceptionally(IllegalStateException("Messenger is stopped"))
            return future
        }

        val id = requestIdGenerator.getAndIncrement()
        val message = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }

        pendingRequests[id] = future
        writeMessage(message.toString())

        return future
    }

    /**
     * 按照 LSP HTTP-like 规范向输出流写入消息。
     */
    private fun writeMessage(jsonBody: String) {
        try {
            val bytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
            val header = "Content-Length: ${bytes.size}\r\n\r\n"
            
            synchronized(outputStream) {
                outputStream.write(header.toByteArray(StandardCharsets.UTF_8))
                outputStream.write(bytes)
                outputStream.flush()
            }
            log.trace("--> Sent: {}", jsonBody.take(300))
        } catch (e: Exception) {
            if (isRunning) {
                log.error("Failed to write message to Clangd", e)
            }
        }
    }

    /**
     * 守护线程读取循环：解析 Headers -> 提取 Body -> 路由。
     */
    private fun readerLoop() {
        try {
            while (isRunning) {
                var contentLength = -1
                
                // 1. 读取 Headers
                while (isRunning) {
                    val line = readLineFromStream() ?: break
                    if (line.isEmpty()) {
                        // 空行标志着 Header 结束，开始读取 Body
                        break
                    }
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1
                    }
                }

                if (!isRunning || contentLength <= 0) {
                    continue
                }

                // 2. 读取指定长度的 Body
                val bodyBytes = ByteArray(contentLength)
                var bytesRead = 0
                while (bytesRead < contentLength && isRunning) {
                    val read = inputStream.read(bodyBytes, bytesRead, contentLength - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }

                if (bytesRead == contentLength) {
                    val jsonString = String(bodyBytes, StandardCharsets.UTF_8)
                    handleIncomingJson(jsonString)
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                log.error("RPC Reader loop crashed", e)
            }
        }
    }

    /**
     * 将获取到的完整 JSON 报文进行分发。
     */
    private fun handleIncomingJson(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            
            if (json.has("id") && (json.has("result") || json.has("error"))) {
                // 这是一个 Response
                val id = json.optLong("id", -1)
                val future = pendingRequests.remove(id)
                if (future != null) {
                    if (json.has("error")) {
                        // 将 Error 转为正常返回以让上层处理，或直接 completeExceptionally
                        log.warn("RPC Error response for id $id: ${json.optJSONObject("error")}")
                    }
                    future.complete(json)
                }
            } else if (json.has("method")) {
                // 这是一个 Notification 或 Server-to-Client Request
                val method = json.getString("method")
                val params = json.optJSONObject("params") ?: JSONObject()
                notificationHandler(method, params)
            }
            
        } catch (e: Exception) {
            log.error("Failed to parse incoming JSON", e)
        }
    }

    /**
     * 逐字节读取到 `\r\n`。
     */
    private fun readLineFromStream(): String? {
        val sb = StringBuilder()
        while (isRunning) {
            val b = inputStream.read()
            if (b == -1) return null
            val c = b.toChar()
            if (c == '\n') {
                if (sb.isNotEmpty() && sb.last() == '\r') {
                    sb.deleteCharAt(sb.length - 1)
                }
                return sb.toString()
            }
            sb.append(c)
        }
        return null
    }
}