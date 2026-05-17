package android.zero.studio.chatai.server.mcp.core

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * MCP 工具管理器。
 *
 * 目标：
 * 1) 所有工具可稳定调用；
 * 2) 所有输出具有明确的行为结果和统计指标；
 * 3) 严格限制在 workspace 内，避免越界。
 */
object McpToolManager {
  private val gson = Gson()
  private const val DEFAULT_LIST_LIMIT = 200
  private const val DEFAULT_SEARCH_LIMIT = 100
  private const val MAX_READ_SIZE_BYTES = 2 * 1024 * 1024L
  private val ignoredDirNames = setOf("build", ".git", ".gradle", "node_modules")

  fun getToolDefinitions(): JsonArray {
    val tools = JsonArray()

    tools.add(
        createToolDef(
            name = "workspace_list",
            desc = "列出工作区内文件/目录。支持分页限制、是否递归、仅目录过滤。",
            schemaJson =
                """
                {
                  "type":"object",
                  "properties":{
                    "path":{"type":"string","description":"相对 workspace 路径，默认 ."},
                    "recursive":{"type":"boolean","description":"是否递归列出，默认 false"},
                    "directoriesOnly":{"type":"boolean","description":"是否仅返回目录，默认 false"},
                    "limit":{"type":"integer","minimum":1,"maximum":1000,"description":"最大返回条目数，默认 200"}
                  }
                }
                """.trimIndent(),
        )
    )

    tools.add(
        createToolDef(
            name = "workspace_read_text",
            desc = "读取 UTF-8 文本文件内容。返回字节数、行数、是否截断等指标。",
            schemaJson =
                """
                {
                  "type":"object",
                  "properties":{
                    "path":{"type":"string"},
                    "startLine":{"type":"integer","minimum":1,"description":"起始行，默认 1"},
                    "endLine":{"type":"integer","minimum":1,"description":"结束行(含)，默认读取全文件"}
                  },
                  "required":["path"]
                }
                """.trimIndent(),
        )
    )

    tools.add(
        createToolDef(
            name = "workspace_write_text",
            desc = "写入 UTF-8 文本文件。支持覆盖或追加，返回写入字节数与最终文件大小。",
            schemaJson =
                """
                {
                  "type":"object",
                  "properties":{
                    "path":{"type":"string"},
                    "content":{"type":"string"},
                    "append":{"type":"boolean","description":"是否追加，默认 false(覆盖)"}
                  },
                  "required":["path","content"]
                }
                """.trimIndent(),
        )
    )

    tools.add(
        createToolDef(
            name = "workspace_search_text",
            desc = "全文搜索文本关键词。返回匹配总数、扫描文件数、结果裁剪状态。",
            schemaJson =
                """
                {
                  "type":"object",
                  "properties":{
                    "keyword":{"type":"string"},
                    "path":{"type":"string","description":"限定在子目录搜索，默认 ."},
                    "caseSensitive":{"type":"boolean","description":"默认 false"},
                    "limit":{"type":"integer","minimum":1,"maximum":1000,"description":"最大返回匹配数，默认 100"}
                  },
                  "required":["keyword"]
                }
                """.trimIndent(),
        )
    )

    tools.add(
        createToolDef(
            name = "workspace_analyze",
            desc = "输出工作区关键结构与统计信息，帮助模型快速建立上下文。",
            schemaJson = """{"type":"object","properties":{}}""",
        )
    )

    return tools
  }

  fun handleCall(name: String, args: JsonObject, rootDir: File): String {
    return try {
      when (name) {
        "workspace_list", "ls" -> workspaceList(rootDir, args)
        "workspace_read_text", "read_file" -> workspaceReadText(rootDir, args)
        "workspace_write_text", "write_file" -> workspaceWriteText(rootDir, args)
        "workspace_search_text", "search_code" -> workspaceSearchText(rootDir, args)
        "workspace_analyze", "get_project_structure" -> workspaceAnalyze(rootDir)
        else -> errorJson("UNKNOWN_TOOL", "Unknown tool '$name'")
      }
    } catch (e: Exception) {
      errorJson("TOOL_EXECUTION_ERROR", "${e.javaClass.simpleName}: ${e.message}")
    }
  }

  private fun workspaceList(root: File, args: JsonObject): String {
    val subPath = args.get("path")?.asString ?: "."
    val recursive = args.get("recursive")?.asBoolean ?: false
    val directoriesOnly = args.get("directoriesOnly")?.asBoolean ?: false
    val limit = (args.get("limit")?.asInt ?: DEFAULT_LIST_LIMIT).coerceIn(1, 1000)

    val target = resolvePath(root, subPath) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!target.exists()) return errorJson("PATH_NOT_FOUND", "Path not found: $subPath")

    val entries = JsonArray()
    var total = 0
    val sequence = if (recursive) target.walkTopDown().drop(1) else (target.listFiles()?.asSequence() ?: emptySequence())
    sequence
        .filter { !ignoredDirNames.contains(it.name) }
        .forEach { f ->
          if (directoriesOnly && !f.isDirectory) return@forEach
          total++
          if (entries.size() < limit) {
            val o = JsonObject()
            o.addProperty("path", f.relativeTo(root).path.replace('\\', '/'))
            o.addProperty("type", if (f.isDirectory) "directory" else "file")
            if (f.isFile) o.addProperty("sizeBytes", f.length())
            entries.add(o)
          }
        }

    return JsonObject().apply {
      addProperty("ok", true)
      addProperty("tool", "workspace_list")
      addProperty("target", target.relativeTo(root).path.ifEmpty { "." })
      addProperty("recursive", recursive)
      addProperty("directoriesOnly", directoriesOnly)
      addProperty("totalEntries", total)
      addProperty("returnedEntries", entries.size())
      addProperty("truncated", total > entries.size())
      add("entries", entries)
    }.toString()
  }

  private fun workspaceReadText(root: File, args: JsonObject): String {
    val path = args.get("path")?.asString ?: return errorJson("INVALID_ARGUMENT", "'path' is required")
    val file = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!file.exists()) return errorJson("FILE_NOT_FOUND", "File not found: $path")
    if (!file.isFile) return errorJson("INVALID_TARGET", "Target is not a file: $path")
    if (file.length() > MAX_READ_SIZE_BYTES) return errorJson("FILE_TOO_LARGE", "File exceeds ${MAX_READ_SIZE_BYTES} bytes")

    val startLine = (args.get("startLine")?.asInt ?: 1).coerceAtLeast(1)
    val endLineArg = args.get("endLine")?.asInt

    val allLines = file.readLines(StandardCharsets.UTF_8)
    val totalLines = allLines.size
    val endLine = (endLineArg ?: totalLines).coerceAtLeast(startLine).coerceAtMost(totalLines.coerceAtLeast(1))
    val selected = if (totalLines == 0) emptyList() else allLines.subList(startLine - 1, endLine)

    return JsonObject().apply {
      addProperty("ok", true)
      addProperty("tool", "workspace_read_text")
      addProperty("path", file.relativeTo(root).path.replace('\\', '/'))
      addProperty("sizeBytes", file.length())
      addProperty("totalLines", totalLines)
      addProperty("returnedStartLine", if (totalLines == 0) 0 else startLine)
      addProperty("returnedEndLine", if (totalLines == 0) 0 else endLine)
      addProperty("returnedLineCount", selected.size)
      addProperty("content", selected.joinToString("\n"))
    }.toString()
  }

  private fun workspaceWriteText(root: File, args: JsonObject): String {
    val path = args.get("path")?.asString ?: return errorJson("INVALID_ARGUMENT", "'path' is required")
    val content = args.get("content")?.asString ?: return errorJson("INVALID_ARGUMENT", "'content' is required")
    val append = args.get("append")?.asBoolean ?: false

    val file = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    file.parentFile?.mkdirs()

    if (append) {
      file.appendText(content, StandardCharsets.UTF_8)
    } else {
      file.writeText(content, StandardCharsets.UTF_8)
    }

    return JsonObject().apply {
      addProperty("ok", true)
      addProperty("tool", "workspace_write_text")
      addProperty("path", file.relativeTo(root).path.replace('\\', '/'))
      addProperty("mode", if (append) "append" else "overwrite")
      addProperty("writtenBytes", content.toByteArray(StandardCharsets.UTF_8).size)
      addProperty("finalSizeBytes", file.length())
    }.toString()
  }

  private fun workspaceSearchText(root: File, args: JsonObject): String {
    val keyword = args.get("keyword")?.asString
    if (keyword.isNullOrBlank()) return errorJson("INVALID_ARGUMENT", "'keyword' is required")
    val subPath = args.get("path")?.asString ?: "."
    val caseSensitive = args.get("caseSensitive")?.asBoolean ?: false
    val limit = (args.get("limit")?.asInt ?: DEFAULT_SEARCH_LIMIT).coerceIn(1, 1000)

    val target = resolvePath(root, subPath) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!target.exists()) return errorJson("PATH_NOT_FOUND", "Path not found: $subPath")

    var scannedFiles = 0
    var totalMatches = 0
    val matches = JsonArray()

    val files = if (target.isFile) sequenceOf(target) else target.walkTopDown().asSequence().filter { it.isFile }
    files
        .filter { !ignoredDirNames.any { dir -> it.path.contains("/$dir/") || it.path.contains("\\$dir\\") } }
        .filter { isTextCandidate(it.name) }
        .forEach { file ->
          scannedFiles++
          runCatching { file.readLines(StandardCharsets.UTF_8) }.getOrNull()?.forEachIndexed { idx, line ->
            val hit = if (caseSensitive) line.contains(keyword) else line.contains(keyword, true)
            if (hit) {
              totalMatches++
              if (matches.size() < limit) {
                val m = JsonObject()
                m.addProperty("path", file.relativeTo(root).path.replace('\\', '/'))
                m.addProperty("line", idx + 1)
                m.addProperty("snippet", line.trim())
                matches.add(m)
              }
            }
          }
        }

    return JsonObject().apply {
      addProperty("ok", true)
      addProperty("tool", "workspace_search_text")
      addProperty("keyword", keyword)
      addProperty("caseSensitive", caseSensitive)
      addProperty("scannedFiles", scannedFiles)
      addProperty("totalMatches", totalMatches)
      addProperty("returnedMatches", matches.size())
      addProperty("truncated", totalMatches > matches.size())
      add("matches", matches)
    }.toString()
  }

  private fun workspaceAnalyze(root: File): String {
    val criticalPaths =
        listOf(
            "src/main/java",
            "src/main/kotlin",
            "src/main/res",
            "src/main/AndroidManifest.xml",
            "build.gradle",
            "build.gradle.kts",
            "settings.gradle",
            "settings.gradle.kts",
        )

    val critical = JsonArray()
    criticalPaths.forEach { p ->
      val e = JsonObject()
      e.addProperty("path", p)
      e.addProperty("exists", File(root, p).exists())
      critical.add(e)
    }

    var dirs = 0
    var files = 0
    root.walkTopDown().forEach { if (it.isDirectory) dirs++ else files++ }

    return JsonObject().apply {
      addProperty("ok", true)
      addProperty("tool", "workspace_analyze")
      addProperty("workspace", root.absolutePath)
      addProperty("directoryCount", dirs)
      addProperty("fileCount", files)
      add("criticalPaths", critical)
    }.toString()
  }

  private fun resolvePath(root: File, subPath: String): File? {
    val rootCanonical = root.canonicalFile
    val target = File(rootCanonical, subPath).canonicalFile
    return if (target.path == rootCanonical.path || target.path.startsWith(rootCanonical.path + File.separator)) target else null
  }

  private fun isTextCandidate(name: String): Boolean {
    val ext = name.substringAfterLast('.', "")
    return setOf("kt", "java", "xml", "gradle", "kts", "properties", "json", "md", "txt", "yaml", "yml").contains(ext)
  }

  private fun errorJson(code: String, message: String): String {
    return JsonObject().apply {
      addProperty("ok", false)
      addProperty("errorCode", code)
      addProperty("message", message)
    }.toString()
  }

  private fun createToolDef(name: String, desc: String, schemaJson: String): JsonObject {
    return JsonObject().apply {
      addProperty("name", name)
      addProperty("description", desc)
      add("inputSchema", gson.fromJson(schemaJson, JsonObject::class.java))
    }
  }
}
