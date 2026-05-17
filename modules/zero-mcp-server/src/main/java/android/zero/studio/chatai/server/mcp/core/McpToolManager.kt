package android.zero.studio.chatai.server.mcp.core

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.nio.charset.StandardCharsets

object McpToolManager {
  private val gson = Gson()
  private const val DEFAULT_LIST_LIMIT = 200
  private const val DEFAULT_SEARCH_LIMIT = 100
  private const val MAX_READ_SIZE_BYTES = 2 * 1024 * 1024L
  private val ignoredDirNames = setOf("build", ".git", ".gradle", "node_modules")

  private val toolNames =
      listOf(
          "workspace_list","workspace_read_text","workspace_write_text","workspace_search_text","workspace_analyze",
          "project_modules_list","gradle_task_list","gradle_task_run","shell_execute","git_status","git_diff",
          "test_unit_run","quality_lint_run","diagnostics_file_get","android_manifest_inspect",
          "system_tools_list","system_tools_enable","system_tools_disable"
      )

  fun getToolDefinitions(): JsonArray {
    val tools = JsonArray()
    toolNames.forEach { name -> tools.add(createToolDef(name, descriptionOf(name), "{\"type\":\"object\",\"properties\":{}}")) }
    return tools
  }

  fun handleCall(name: String, args: JsonObject, rootDir: File): String {
    if (!ToolControlCenter.isEnabled(name)) return errorJson("TOOL_DISABLED", "Tool is disabled: $name")
    return try {
      when (name) {
        "workspace_list", "ls" -> workspaceList(rootDir, args)
        "workspace_read_text", "read_file" -> workspaceReadText(rootDir, args)
        "workspace_write_text", "write_file" -> workspaceWriteText(rootDir, args)
        "workspace_search_text", "search_code" -> workspaceSearchText(rootDir, args)
        "workspace_analyze", "get_project_structure" -> workspaceAnalyze(rootDir)
        "project_modules_list" -> projectModulesList(rootDir)
        "gradle_task_list" -> runCommand(rootDir, listOf("./gradlew", "tasks", "--all"), "gradle_task_list")
        "gradle_task_run" -> {
          val task = args.get("task")?.asString ?: return errorJson("INVALID_ARGUMENT", "'task' is required")
          runCommand(rootDir, listOf("./gradlew", task), "gradle_task_run")
        }
        "shell_execute" -> {
          val command = args.get("command")?.asString ?: return errorJson("INVALID_ARGUMENT", "'command' is required")
          runCommand(rootDir, listOf("sh", "-c", command), "shell_execute")
        }
        "git_status" -> runCommand(rootDir, listOf("git", "status", "--short", "--branch"), "git_status")
        "git_diff" -> runCommand(rootDir, listOf("git", "diff"), "git_diff")
        "test_unit_run" -> runCommand(rootDir, listOf("./gradlew", "test"), "test_unit_run")
        "quality_lint_run" -> runCommand(rootDir, listOf("./gradlew", "lint"), "quality_lint_run")
        "diagnostics_file_get" -> workspaceReadText(rootDir, args)
        "android_manifest_inspect" -> androidManifestInspect(rootDir, args)
        "system_tools_list" -> systemToolsList()
        "system_tools_enable" -> systemToolsToggle(args, true)
        "system_tools_disable" -> systemToolsToggle(args, false)
        else -> errorJson("UNKNOWN_TOOL", "Unknown tool '$name'")
      }
    } catch (e: Exception) {
      errorJson("TOOL_EXECUTION_ERROR", "${e.javaClass.simpleName}: ${e.message}")
    }
  }

  private fun projectModulesList(root: File): String {
    val settings = listOf(File(root, "settings.gradle.kts"), File(root, "settings.gradle")).firstOrNull { it.exists() }
        ?: return errorJson("FILE_NOT_FOUND", "settings.gradle(.kts) not found")
    val includes = settings.readLines().filter { it.contains(":") && it.contains("\"") }
    return ok("project_modules_list").apply {
      addProperty("settingsFile", settings.name)
      addProperty("count", includes.size)
      addProperty("content", includes.joinToString("\n"))
    }.toString()
  }

  private fun androidManifestInspect(root: File, args: JsonObject): String {
    val path = args.get("path")?.asString ?: "app/src/main/AndroidManifest.xml"
    val f = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!f.exists()) return errorJson("FILE_NOT_FOUND", "Manifest not found: $path")
    val txt = f.readText(StandardCharsets.UTF_8)
    return ok("android_manifest_inspect").apply {
      addProperty("path", path)
      addProperty("hasApplication", txt.contains("<application"))
      addProperty("permissionCount", "<uses-permission".toRegex().findAll(txt).count())
      addProperty("activityCount", "<activity".toRegex().findAll(txt).count())
      addProperty("serviceCount", "<service".toRegex().findAll(txt).count())
    }.toString()
  }

  private fun systemToolsList(): String {
    val arr = JsonArray()
    toolNames.forEach { n -> arr.add(JsonObject().apply { addProperty("name", n); addProperty("enabled", ToolControlCenter.isEnabled(n)) }) }
    return ok("system_tools_list").apply { add("tools", arr) }.toString()
  }

  private fun systemToolsToggle(args: JsonObject, enabled: Boolean): String {
    val name = args.get("toolName")?.asString ?: return errorJson("INVALID_ARGUMENT", "'toolName' is required")
    ToolControlCenter.setEnabled(name, enabled)
    return ok(if (enabled) "system_tools_enable" else "system_tools_disable").apply {
      addProperty("toolName", name); addProperty("enabled", enabled)
    }.toString()
  }

  private fun runCommand(root: File, command: List<String>, tool: String): String {
    val p = ProcessBuilder(command).directory(root).redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    val code = p.waitFor()
    return ok(tool).apply { addProperty("exitCode", code); addProperty("output", out.take(200000)) }.toString()
  }

  private fun workspaceList(root: File, args: JsonObject): String { /* same as before */
    val subPath = args.get("path")?.asString ?: "."; val recursive = args.get("recursive")?.asBoolean ?: false
    val directoriesOnly = args.get("directoriesOnly")?.asBoolean ?: false; val limit = (args.get("limit")?.asInt ?: DEFAULT_LIST_LIMIT).coerceIn(1, 1000)
    val target = resolvePath(root, subPath) ?: return errorJson("ACCESS_DENIED", "Path outside workspace"); if (!target.exists()) return errorJson("PATH_NOT_FOUND", "Path not found: $subPath")
    val entries = JsonArray(); var total = 0; val seq = if (recursive) target.walkTopDown().drop(1) else (target.listFiles()?.asSequence() ?: emptySequence())
    seq.filter { !ignoredDirNames.contains(it.name) }.forEach { f -> if (directoriesOnly && !f.isDirectory) return@forEach; total++; if (entries.size() < limit) entries.add(JsonObject().apply { addProperty("path", f.relativeTo(root).path.replace('\\','/')); addProperty("type", if (f.isDirectory) "directory" else "file"); if (f.isFile) addProperty("sizeBytes", f.length()) }) }
    return ok("workspace_list").apply { addProperty("totalEntries", total); addProperty("returnedEntries", entries.size()); addProperty("truncated", total > entries.size()); add("entries", entries) }.toString()
  }

  private fun workspaceReadText(root: File, args: JsonObject): String {
    val path = args.get("path")?.asString ?: return errorJson("INVALID_ARGUMENT", "'path' is required")
    val file = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!file.exists()) return errorJson("FILE_NOT_FOUND", "File not found: $path"); if (!file.isFile) return errorJson("INVALID_TARGET", "Target is not a file: $path")
    if (file.length() > MAX_READ_SIZE_BYTES) return errorJson("FILE_TOO_LARGE", "File exceeds ${MAX_READ_SIZE_BYTES} bytes")
    val allLines = file.readLines(StandardCharsets.UTF_8); val start = (args.get("startLine")?.asInt ?: 1).coerceAtLeast(1); val end = (args.get("endLine")?.asInt ?: allLines.size).coerceAtLeast(start).coerceAtMost(allLines.size.coerceAtLeast(1))
    val selected = if (allLines.isEmpty()) emptyList() else allLines.subList(start - 1, end)
    return ok("workspace_read_text").apply { addProperty("path", path); addProperty("content", selected.joinToString("\n")); addProperty("totalLines", allLines.size) }.toString()
  }

  private fun workspaceWriteText(root: File, args: JsonObject): String {
    val path = args.get("path")?.asString ?: return errorJson("INVALID_ARGUMENT", "'path' is required"); val content = args.get("content")?.asString ?: return errorJson("INVALID_ARGUMENT", "'content' is required")
    val append = args.get("append")?.asBoolean ?: false; val file = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace"); file.parentFile?.mkdirs(); if (append) file.appendText(content, StandardCharsets.UTF_8) else file.writeText(content, StandardCharsets.UTF_8)
    return ok("workspace_write_text").apply { addProperty("path", path); addProperty("finalSizeBytes", file.length()) }.toString()
  }

  private fun workspaceSearchText(root: File, args: JsonObject): String {
    val keyword = args.get("keyword")?.asString ?: return errorJson("INVALID_ARGUMENT", "'keyword' is required")
    val subPath = args.get("path")?.asString ?: "."; val limit = (args.get("limit")?.asInt ?: DEFAULT_SEARCH_LIMIT).coerceIn(1, 1000)
    val target = resolvePath(root, subPath) ?: return errorJson("ACCESS_DENIED", "Path outside workspace"); if (!target.exists()) return errorJson("PATH_NOT_FOUND", "Path not found: $subPath")
    val matches = JsonArray(); var total = 0
    val files = if (target.isFile) sequenceOf(target) else target.walkTopDown().asSequence().filter { it.isFile }
    files.filter { isTextCandidate(it.name) }.forEach { f -> runCatching { f.readLines(StandardCharsets.UTF_8) }.getOrNull()?.forEachIndexed { idx, line -> if (line.contains(keyword, true)) { total++; if (matches.size() < limit) matches.add(JsonObject().apply { addProperty("path", f.relativeTo(root).path.replace('\\','/')); addProperty("line", idx + 1); addProperty("snippet", line.trim()) }) } } }
    return ok("workspace_search_text").apply { addProperty("totalMatches", total); add("matches", matches); addProperty("truncated", total > matches.size()) }.toString()
  }

  private fun workspaceAnalyze(root: File): String = ok("workspace_analyze").apply { addProperty("workspace", root.absolutePath) }.toString()

  private fun resolvePath(root: File, subPath: String): File? { val rc = root.canonicalFile; val t = File(rc, subPath).canonicalFile; return if (t.path == rc.path || t.path.startsWith(rc.path + File.separator)) t else null }
  private fun isTextCandidate(name: String): Boolean = setOf("kt","java","xml","gradle","kts","properties","json","md","txt","yaml","yml").contains(name.substringAfterLast('.', ""))
  private fun ok(tool: String): JsonObject = JsonObject().apply { addProperty("ok", true); addProperty("tool", tool) }
  private fun errorJson(code: String, message: String): String = JsonObject().apply { addProperty("ok", false); addProperty("errorCode", code); addProperty("message", message) }.toString()
  private fun createToolDef(name: String, desc: String, schemaJson: String): JsonObject = JsonObject().apply { addProperty("name", name); addProperty("description", desc); addProperty("enabled", ToolControlCenter.isEnabled(name)); add("inputSchema", gson.fromJson(schemaJson, JsonObject::class.java)) }
  private fun descriptionOf(name: String): String = when (name) {
    "project_modules_list" -> "列出工程模块列表"
    "gradle_task_list" -> "列出 Gradle Tasks"
    "gradle_task_run" -> "运行指定 Gradle Task"
    "shell_execute" -> "执行 shell 命令"
    "git_status" -> "获取 git status"
    "git_diff" -> "获取 git diff"
    "test_unit_run" -> "运行 unit tests"
    "quality_lint_run" -> "运行 lint"
    "diagnostics_file_get" -> "获取文件诊断基础信息"
    "android_manifest_inspect" -> "解析 AndroidManifest 基础结构"
    "system_tools_list" -> "列出工具开关状态"
    "system_tools_enable" -> "启用指定工具"
    "system_tools_disable" -> "禁用指定工具"
    else -> "MCP tool: $name"
  }
}
