package android.zero.studio.lsp.clangd

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import org.slf4j.LoggerFactory

class JsonRpcMessenger(
    input: InputStream,
    private val output: OutputStream,
    private val notificationHandler: (String, JSONObject) -> Unit = { _, _ -> },
) : AutoCloseable {
  private val log = LoggerFactory.getLogger(javaClass)
  private val input = BufferedInputStream(input)
  private val ids = AtomicLong(1)
  private val pending = ConcurrentHashMap<Long, CompletableFuture<JSONObject>>()
  private val reader = Executors.newSingleThreadExecutor { r -> Thread(r, "clangd-jsonrpc-reader").apply { isDaemon = true } }
  @Volatile private var running = false

  fun start() {
    if (running) return
    running = true
    reader.execute(::readLoop)
  }

  fun sendRequest(method: String, params: JSONObject = JSONObject()): CompletableFuture<JSONObject> {
    val id = ids.getAndIncrement()
    val request = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params)
    val future = CompletableFuture<JSONObject>()
    pending[id] = future
    writeMessage(request)
    return future
  }

  fun sendNotification(method: String, params: JSONObject = JSONObject()) {
    writeMessage(JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params))
  }

  @Synchronized private fun writeMessage(message: JSONObject) {
    val body = message.toString().toByteArray(StandardCharsets.UTF_8)
    val header = "Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
    output.write(header)
    output.write(body)
    output.flush()
  }

  private fun readLoop() {
    while (running) {
      try {
        val length = readContentLength() ?: break
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
          val read = input.read(body, offset, length - offset)
          if (read < 0) throw IllegalStateException("clangd closed stdout")
          offset += read
        }
        handleMessage(JSONObject(String(body, StandardCharsets.UTF_8)))
      } catch (t: Throwable) {
        if (running) log.error("clangd JSON-RPC reader failed", t)
        break
      }
    }
    running = false
    pending.values.forEach { it.completeExceptionally(IllegalStateException("clangd JSON-RPC connection closed")) }
    pending.clear()
  }

  private fun readContentLength(): Int? {
    var contentLength: Int? = null
    while (true) {
      val line = readAsciiLine() ?: return null
      if (line.isEmpty()) return contentLength ?: throw IllegalStateException("Missing Content-Length header")
      val colon = line.indexOf(':')
      if (colon <= 0) continue
      if (line.substring(0, colon).equals("Content-Length", ignoreCase = true)) {
        contentLength = line.substring(colon + 1).trim().toInt()
      }
    }
  }

  private fun readAsciiLine(): String? {
    val bytes = ArrayList<Byte>(64)
    while (true) {
      val b = input.read()
      if (b < 0) return null
      if (b == '\n'.code) break
      if (b != '\r'.code) bytes.add(b.toByte())
    }
    return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
  }

  private fun handleMessage(message: JSONObject) {
    val id = message.optLong("id", Long.MIN_VALUE)
    if (id != Long.MIN_VALUE && (message.has("result") || message.has("error"))) {
      val future = pending.remove(id)
      if (message.has("error")) {
        future?.completeExceptionally(IllegalStateException(message.optJSONObject("error")?.optString("message") ?: message.optString("error")))
      } else {
        future?.complete(message)
      }
      return
    }
    val method = message.optString("method", "")
    if (method.isNotBlank()) notificationHandler(method, message.optJSONObject("params") ?: JSONObject())
  }

  override fun close() {
    running = false
    reader.shutdownNow()
    runCatching { input.close() }
    runCatching { output.close() }
  }
}
