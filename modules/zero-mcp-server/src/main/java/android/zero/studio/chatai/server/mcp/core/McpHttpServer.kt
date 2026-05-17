package android.zero.studio.chatai.server.mcp.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD
import java.io.File

/**
 * 嵌入式 MCP HTTP 服务器 监听端口，处理 JSON-RPC 请求并分发给 ToolManager
 *
 * @author android_zero
 */
class McpHttpServer(
    port: Int,
    private val workspace: File,
    private val logCallback: (String) -> Unit,
) : NanoHTTPD(port) {

  private val gson = Gson()

  override fun serve(session: IHTTPSession): Response {
    val uri = session.uri
    val method = session.method

    // CORS Headers (允许跨域访问)
    val corsHeaders =
        mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, Authorization",
        )

    // 处理预检请求
    if (Method.OPTIONS == method) {
      val res = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
      corsHeaders.forEach { (k, v) -> res.addHeader(k, v) }
      return res
    }

    // 处理 JSON-RPC POST 请求
    if (method == Method.POST && (uri == "/ZeroStudio" || uri == "/")) {
      val map = HashMap<String, String>()
      try {
        session.parseBody(map)
        val jsonBody = map["postData"] ?: return newFixedLengthResponse("Missing body")

        // 解析请求
        val request = JsonParser.parseString(jsonBody).asJsonObject

        // 处理 RPC 逻辑
        val response = handleRpc(request)

        // 如果是通知类型的请求(handleRpc返回null)，返回空响应
        val responseBody = if (response != null) gson.toJson(response) else ""

        val res = newFixedLengthResponse(Response.Status.OK, "application/json", responseBody)
        corsHeaders.forEach { (k, v) -> res.addHeader(k, v) }
        return res
      } catch (e: Exception) {
        logCallback("RPC Error: ${e.message}")
        // 返回 JSON 格式的错误，符合 JSON-RPC 规范
        val errorResponse =
            JsonObject().apply {
              addProperty("jsonrpc", "2.0")
              add(
                  "error",
                  JsonObject().apply {
                    addProperty("code", -32603)
                    addProperty("message", "Internal Error: ${e.message}")
                  },
              )
              add("id", null)
            }
        val res =
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                gson.toJson(errorResponse),
            )
        corsHeaders.forEach { (k, v) -> res.addHeader(k, v) }
        return res
      }
    }

    return newFixedLengthResponse(
        Response.Status.OK,
        MIME_HTML,
        "<html><body><h1>Android MCP Server is Running</h1><p>Workspace: ${workspace.name}</p></body></html>",
    )
  }

  private fun handleRpc(request: JsonObject): JsonObject? {
    val method = request.get("method")?.asString ?: return null

    val idElement = request.get("id")
    val id = if (idElement != null && !idElement.isJsonNull) idElement else null

    // 准备响应对象
    val response = JsonObject()
    response.addProperty("jsonrpc", "2.0")
    if (id != null) {
      response.add("id", id)
    }

    when (method) {
      "initialize" -> {
        logCallback("Client Handshake: Initialize")
        val result = JsonObject()
        result.addProperty("protocolVersion", "2024-11-05")

        val caps = JsonObject()
        caps.add("tools", JsonObject()) // 支持 Tools 能力
        result.add("capabilities", caps)

        val serverInfo = JsonObject()
        serverInfo.addProperty("name", "AndroidStudioMcp")
        serverInfo.addProperty("version", "2.0")
        result.add("serverInfo", serverInfo)

        response.add("result", result)
      }
      "notifications/initialized" -> {
        // 客户端确认初始化完成，无需响应
        return null
      }
      "tools/list" -> {
        // 返回工具定义
        val result = JsonObject()
        result.add("tools", McpToolManager.getToolDefinitions())
        response.add("result", result)
      }
      "tools/call" -> {
        val paramsElement = request.get("params")
        val params =
            if (paramsElement != null && !paramsElement.isJsonNull) paramsElement.asJsonObject
            else JsonObject()

        val toolName = params.get("name")?.asString ?: "unknown"

        val argsElement = params.get("arguments")
        val args =
            if (argsElement != null && !argsElement.isJsonNull) argsElement.asJsonObject
            else JsonObject()

        logCallback("Exec: $toolName")

        // 执行工具逻辑
        val outputText = McpToolManager.handleCall(toolName, args, workspace)

        // 构造响应
        val result = JsonObject()
        val content = JsonObject()
        content.addProperty("type", "text")
        content.addProperty("text", outputText)

        val contentArray = com.google.gson.JsonArray()
        contentArray.add(content)
        result.add("content", contentArray)

        response.add("result", result)
      }
      else -> {
        logCallback("Warn: Unknown method $method")
        // 对于未知方法，如果是 Call，应该返回错误；如果是 Notify，忽略
        if (id != null) {
          val error = JsonObject()
          error.addProperty("code", -32601)
          error.addProperty("message", "Method not found")
          response.add("error", error)
        } else {
          return null
        }
      }
    }
    return response
  }
}
