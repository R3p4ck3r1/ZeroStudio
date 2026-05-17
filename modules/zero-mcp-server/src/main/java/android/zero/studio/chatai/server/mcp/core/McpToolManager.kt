package android.zero.studio.chatai.server.mcp.core

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * MCP 工具管理器 实现了 AI 编程所需的工具集：文件读写、代码搜索、项目结构分析。
 *
 * @author android_zero
 */
object McpToolManager {
  private val gson = Gson()

  /** 定义 AI 可调用的工具列表 (MCP Protocol - tools/list) */
  fun getToolDefinitions(): JsonArray {
    val tools = JsonArray()

    // 列出文件 (ls)
    tools.add(
        createToolDef(
            "ls",
            "列出当前工作区的文件和文件夹结构，用于了解项目目录。",
            """{"type":"object", "properties":{"path":{"type":"string", "description":"相对路径，为空则列出根目录"}}}""",
        )
    )

    // 读取文件 (read_file) - AI 读取代码上下文
    tools.add(
        createToolDef(
            "read_file",
            "读取源代码文件内容 (.java, .kt, .xml, .gradle 等)。",
            """{"type":"object", "properties":{"path":{"type":"string"}}, "required":["path"]}""",
        )
    )

    // 写入文件 (write_file) - AI 写代码核心能力
    tools.add(
        createToolDef(
            "write_file",
            "创建新文件或覆盖现有文件内容。用于编写代码、生成配置文件。",
            """{"type":"object", "properties":{"path":{"type":"string"}, "content":{"type":"string"}}, "required":["path", "content"]}""",
        )
    )

    // 全局搜索 (search_code)
    tools.add(
        createToolDef(
            "search_code",
            "在项目中递归搜索代码片段、类名或关键词。",
            """{"type":"object", "properties":{"keyword":{"type":"string"}}, "required":["keyword"]}""",
        )
    )

    // 获取项目概览 (get_project_structure)
    tools.add(
        createToolDef(
            "get_project_structure",
            "快速分析项目结构，识别 src, res, Manifest, Gradle 等关键位置。",
            """{"type":"object", "properties":{}}""",
        )
    )

    return tools
  }

  /** 处理工具调用 (MCP Protocol - tools/call) */
  fun handleCall(name: String, args: JsonObject, rootDir: File): String {
    return try {
      when (name) {
        "ls" -> listFiles(rootDir, args.get("path")?.asString)
        "read_file" -> readFile(rootDir, args.get("path")?.asString)
        "write_file" ->
            writeFile(rootDir, args.get("path")?.asString, args.get("content")?.asString)
        "search_code" -> searchCode(rootDir, args.get("keyword")?.asString)
        "get_project_structure" -> analyzeProject(rootDir)
        else -> "Error: Unknown tool '$name'"
      }
    } catch (e: Exception) {
      "Error executing $name: ${e.message}"
    }
  }

  private fun listFiles(root: File, subPath: String?): String {
    val target = if (subPath.isNullOrEmpty()) root else File(root, subPath)
    // 安全检查：防止访问工作区外部
    if (!target.canonicalPath.startsWith(root.canonicalPath))
        return "Error: Access denied (Outside workspace)"
    if (!target.exists()) return "Error: Path not found"

    val sb = StringBuilder()
    if (target.isFile) return "[FILE] ${target.name}"

    target.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))?.forEach { file ->
      val type = if (file.isDirectory) "[DIR] " else "[FILE]"
      sb.append(String.format("%-7s %s\n", type, file.name))
    } ?: return "Empty directory"
    return sb.toString()
  }

  private fun readFile(root: File, path: String?): String {
    if (path == null) return "Error: Path required"
    val file = File(root, path)
    if (!file.canonicalPath.startsWith(root.canonicalPath)) return "Error: Access denied"
    if (!file.exists()) return "Error: File not found: $path"
    if (file.isDirectory) return "Error: Cannot read a directory"
    if (file.length() > 2 * 1024 * 1024) return "Error: File too large (>2MB) for context."

    return try {
      file.readText(StandardCharsets.UTF_8)
    } catch (e: Exception) {
      "Error reading file: ${e.message}"
    }
  }

  private fun writeFile(root: File, path: String?, content: String?): String {
    if (path == null || content == null) return "Error: Path and content required"
    val file = File(root, path)
    if (!file.canonicalPath.startsWith(root.canonicalPath)) return "Error: Access denied"

    return try {
      file.parentFile?.mkdirs() // 自动创建父目录
      file.writeText(content, StandardCharsets.UTF_8)
      "Success: Written ${content.length} bytes to $path"
    } catch (e: Exception) {
      "Error writing file: ${e.message}"
    }
  }

  private fun searchCode(root: File, keyword: String?): String {
    if (keyword.isNullOrEmpty()) return "Error: Keyword required"
    val sb = StringBuilder()
    var matches = 0

    root
        .walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
        .filter { it.isFile && isCodeFile(it.name) }
        .forEach { file ->
          if (matches >= 50) return@forEach
          try {
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
              if (line.contains(keyword, ignoreCase = true)) {
                val relPath = file.relativeTo(root).path
                sb.append("[$relPath:${index + 1}] ${line.trim()}\n")
                matches++
                if (matches >= 50) return@forEach
              }
            }
          } catch (e: Exception) {
            /* Ignore binary or unreadable files */
          }
        }

    return if (sb.isEmpty()) "No matches found." else sb.toString()
  }

  private fun analyzeProject(root: File): String {
    val sb = StringBuilder("Project Structure Analysis:\n")
    val criticalPaths =
        listOf(
            "src/main/java",
            "src/main/kotlin",
            "src/main/res",
            "AndroidManifest.xml",
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
        )

    criticalPaths.forEach { path ->
      val f = File(root, path)
      if (f.exists()) {
        sb.append("✅ Found: $path\n")
      } else {
        sb.append("❌ Missing: $path\n")
      }
    }
    return sb.toString()
  }

  private fun isCodeFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "")
    return setOf("kt", "java", "xml", "gradle", "properties", "json", "md", "txt").contains(ext)
  }

  private fun createToolDef(name: String, desc: String, schemaJson: String): JsonObject {
    val tool = JsonObject()
    tool.addProperty("name", name)
    tool.addProperty("description", desc)
    tool.add("inputSchema", gson.fromJson(schemaJson, JsonObject::class.java))
    return tool
  }
}
