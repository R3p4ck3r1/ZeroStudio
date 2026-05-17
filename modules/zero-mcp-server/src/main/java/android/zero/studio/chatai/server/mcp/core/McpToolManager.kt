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

  data class ToolSpec(
      val name: String,
      val desc: String,
      val capabilities: List<String>,
      val example: String,
      val schemaJson: String = """{"type":"object","properties":{}}""",
  )

  private val specs =
      listOf(
          ToolSpec("workspace_list", "列出工作区目录/文件", listOf("workspace", "read"), "{\"path\":\".\",\"recursive\":true,\"limit\":100}"),
          ToolSpec("workspace_read_text", "读取文本文件内容", listOf("workspace", "read", "text"), "{\"path\":\"app/src/main/AndroidManifest.xml\",\"startLine\":1,\"endLine\":80}"),
          ToolSpec("workspace_write_text", "写入文本文件（覆盖/追加）", listOf("workspace", "write", "text"), "{\"path\":\"README.md\",\"content\":\"hello\",\"append\":false}"),
          ToolSpec("workspace_search_text", "搜索工作区文本", listOf("workspace", "search"), "{\"path\":\".\",\"keyword\":\"McpService\",\"limit\":20}"),
          ToolSpec("workspace_analyze", "分析工作区结构", listOf("workspace", "analysis"), "{}"),
          ToolSpec("project_modules_list", "解析 settings.gradle(.kts) 输出模块列表", listOf("project", "gradle", "modules"), "{}"),
          ToolSpec("gradle_task_list", "执行 ./gradlew tasks --all", listOf("gradle", "task", "build"), "{}"),
          ToolSpec("gradle_task_run", "运行指定 gradle task", listOf("gradle", "task", "build", "execute"), "{\"task\":\":app:assembleDebug\"}"),
          ToolSpec("shell_execute", "执行 shell 命令（当前工作区）", listOf("shell", "execute"), "{\"command\":\"ls -la\"}"),
          ToolSpec("git_status", "执行 git status --short --branch", listOf("git", "status"), "{}"),
          ToolSpec("git_diff", "执行 git diff", listOf("git", "diff"), "{}"),
          ToolSpec("test_unit_run", "执行 ./gradlew test", listOf("test", "gradle"), "{}"),
          ToolSpec("quality_lint_run", "执行 ./gradlew lint", listOf("quality", "lint"), "{}"),
          ToolSpec("diagnostics_file_get", "读取文件诊断基础上下文（当前实现=读取文件）", listOf("diagnostics", "read"), "{\"path\":\"app/build.gradle.kts\"}"),
          ToolSpec("android_manifest_inspect", "解析 AndroidManifest 关键计数", listOf("android", "manifest", "inspect"), "{\"path\":\"app/src/main/AndroidManifest.xml\"}"),
          ToolSpec("system_tools_list", "列出全部工具开关状态", listOf("system", "tool-control"), "{}"),
          ToolSpec("system_tools_enable", "启用指定工具", listOf("system", "tool-control", "write"), "{\"toolName\":\"shell_execute\"}"),
          ToolSpec("system_tools_disable", "禁用指定工具", listOf("system", "tool-control", "write"), "{\"toolName\":\"shell_execute\"}"),
          ToolSpec("system_tool_info", "获取单个工具详细信息（描述/能力/示例/schema）", listOf("system", "tool-metadata"), "{\"toolName\":\"workspace_read_text\"}"),
      )

  private val toolNames = specs.map { it.name }

  fun getToolDefinitions(): JsonArray {
    val tools = JsonArray()
    specs.forEach { spec -> tools.add(createToolDef(spec)) }
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
        "gradle_task_run" -> runGradleTask(rootDir, args)
        "shell_execute" -> shellExecute(rootDir, args)
        "git_status" -> runCommand(rootDir, listOf("git", "status", "--short", "--branch"), "git_status")
        "git_diff" -> runCommand(rootDir, listOf("git", "diff"), "git_diff")
        "test_unit_run" -> runCommand(rootDir, listOf("./gradlew", "test"), "test_unit_run")
        "quality_lint_run" -> runCommand(rootDir, listOf("./gradlew", "lint"), "quality_lint_run")
        "diagnostics_file_get" -> workspaceReadText(rootDir, args)
        "android_manifest_inspect" -> androidManifestInspect(rootDir, args)
        "system_tools_list" -> systemToolsList()
        "system_tools_enable" -> systemToolsToggle(args, true)
        "system_tools_disable" -> systemToolsToggle(args, false)
        "system_tool_info" -> systemToolInfo(args)
        else -> errorJson("UNKNOWN_TOOL", "Unknown tool '$name'")
      }
    } catch (e: Exception) {
      errorJson("TOOL_EXECUTION_ERROR", "${e.javaClass.simpleName}: ${e.message}")
    }
  }

  private fun runGradleTask(root: File, args: JsonObject): String {
    val task = args.get("task")?.asString ?: return errorJson("INVALID_ARGUMENT", "'task' is required")
    return runCommand(root, listOf("./gradlew", task), "gradle_task_run")
  }

  private fun shellExecute(root: File, args: JsonObject): String {
    val command = args.get("command")?.asString ?: return errorJson("INVALID_ARGUMENT", "'command' is required")
    return runCommand(root, listOf("sh", "-c", command), "shell_execute")
  }

  private fun systemToolInfo(args: JsonObject): String {
    val toolName = args.get("toolName")?.asString ?: return errorJson("INVALID_ARGUMENT", "'toolName' is required")
    val spec = specs.firstOrNull { it.name == toolName } ?: return errorJson("NOT_FOUND", "Unknown tool spec: $toolName")
    return ok("system_tool_info").apply {
      add("tool", createToolDef(spec))
    }.toString()
  }

  private fun projectModulesList(root: File): String { val settings = listOf(File(root, "settings.gradle.kts"), File(root, "settings.gradle")).firstOrNull { it.exists() } ?: return errorJson("FILE_NOT_FOUND", "settings.gradle(.kts) not found"); val includes = settings.readLines().filter { it.contains(":") && it.contains("\"") }; return ok("project_modules_list").apply { addProperty("settingsFile", settings.name); addProperty("count", includes.size); addProperty("content", includes.joinToString("\n")) }.toString() }
  private fun androidManifestInspect(root: File, args: JsonObject): String { val path = args.get("path")?.asString ?: "app/src/main/AndroidManifest.xml"; val f = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace"); if (!f.exists()) return errorJson("FILE_NOT_FOUND", "Manifest not found: $path"); val txt = f.readText(StandardCharsets.UTF_8); return ok("android_manifest_inspect").apply { addProperty("path", path); addProperty("hasApplication", txt.contains("<application")); addProperty("permissionCount", "<uses-permission".toRegex().findAll(txt).count()); addProperty("activityCount", "<activity".toRegex().findAll(txt).count()); addProperty("serviceCount", "<service".toRegex().findAll(txt).count()) }.toString() }
  private fun systemToolsList(): String { val arr = JsonArray(); specs.forEach { s -> arr.add(createToolDef(s)) }; return ok("system_tools_list").apply { add("tools", arr) }.toString() }
  private fun systemToolsToggle(args: JsonObject, enabled: Boolean): String { val name = args.get("toolName")?.asString ?: return errorJson("INVALID_ARGUMENT", "'toolName' is required"); ToolControlCenter.setEnabled(name, enabled); return ok(if (enabled) "system_tools_enable" else "system_tools_disable").apply { addProperty("toolName", name); addProperty("enabled", enabled) }.toString() }
  private fun runCommand(root: File, command: List<String>, tool: String): String { val p = ProcessBuilder(command).directory(root).redirectErrorStream(true).start(); val out = p.inputStream.bufferedReader().readText(); val code = p.waitFor(); return ok(tool).apply { addProperty("exitCode", code); addProperty("command", command.joinToString(" ")); addProperty("output", out.take(200000)) }.toString() }

  private fun workspaceList(root: File, args: JsonObject): String { val subPath = args.get("path")?.asString ?: "."; val recursive = args.get("recursive")?.asBoolean ?: false; val directoriesOnly = args.get("directoriesOnly")?.asBoolean ?: false; val limit = (args.get("limit")?.asInt ?: DEFAULT_LIST_LIMIT).coerceIn(1, 1000); val target = resolvePath(root, subPath) ?: return errorJson("ACCESS_DENIED", "Path outside workspace"); if (!target.exists()) return errorJson("PATH_NOT_FOUND", "Path not found: $subPath"); val entries = JsonArray(); var total = 0; val seq = if (recursive) target.walkTopDown().drop(1) else (target.listFiles()?.asSequence() ?: emptySequence()); seq.filter { !ignoredDirNames.contains(it.name) }.forEach { f -> if (directoriesOnly && !f.isDirectory) return@forEach; total++; if (entries.size() < limit) entries.add(JsonObject().apply { addProperty("path", f.relativeTo(root).path.replace('\\','/')); addProperty("type", if (f.isDirectory) "directory" else "file"); if (f.isFile) addProperty("sizeBytes", f.length()) }) }; return ok("workspace_list").apply { addProperty("totalEntries", total); addProperty("returnedEntries", entries.size()); addProperty("truncated", total > entries.size()); add("entries", entries) }.toString() }
  private fun workspaceReadText(root: File, args: JsonObject): String { val path = args.get("path")?.asString ?: return errorJson("INVALID_ARGUMENT", "'path' is required"); val file = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace"); if (!file.exists()) return errorJson("FILE_NOT_FOUND", "File not found: $path"); if (!file.isFile) return errorJson("INVALID_TARGET", "Target is not a file: $path"); if (file.length() > MAX_READ_SIZE_BYTES) return errorJson("FILE_TOO_LARGE", "File exceeds ${MAX_READ_SIZE_BYTES} bytes"); val allLines = file.readLines(StandardCharsets.UTF_8); val start = (args.get("startLine")?.asInt ?: 1).coerceAtLeast(1); val end = (args.get("endLine")?.asInt ?: allLines.size).coerceAtLeast(start).coerceAtMost(allLines.size.coerceAtLeast(1)); val selected = if (allLines.isEmpty()) emptyList() else allLines.subList(start - 1, end); return ok("workspace_read_text").apply { addProperty("path", path); addProperty("content", selected.joinToString("\n")); addProperty("totalLines", allLines.size) }.toString() }
  private fun workspaceWriteText(root: File, args: JsonObject): String { val path = args.get("path")?.asString ?: return errorJson("INVALID_ARGUMENT", "'path' is required"); val content = args.get("content")?.asString ?: return errorJson("INVALID_ARGUMENT", "'content' is required"); val append = args.get("append")?.asBoolean ?: false; val file = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace"); file.parentFile?.mkdirs(); if (append) file.appendText(content, StandardCharsets.UTF_8) else file.writeText(content, StandardCharsets.UTF_8); return ok("workspace_write_text").apply { addProperty("path", path); addProperty("finalSizeBytes", file.length()) }.toString() }
  private fun workspaceSearchText(root: File, args: JsonObject): String { val keyword = args.get("keyword")?.asString ?: return errorJson("INVALID_ARGUMENT", "'keyword' is required"); val subPath = args.get("path")?.asString ?: "."; val limit = (args.get("limit")?.asInt ?: DEFAULT_SEARCH_LIMIT).coerceIn(1, 1000); val target = resolvePath(root, subPath) ?: return errorJson("ACCESS_DENIED", "Path outside workspace"); if (!target.exists()) return errorJson("PATH_NOT_FOUND", "Path not found: $subPath"); val matches = JsonArray(); var total = 0; val files = if (target.isFile) sequenceOf(target) else target.walkTopDown().asSequence().filter { it.isFile }; files.filter { isTextCandidate(it.name) }.forEach { f -> runCatching { f.readLines(StandardCharsets.UTF_8) }.getOrNull()?.forEachIndexed { idx, line -> if (line.contains(keyword, true)) { total++; if (matches.size() < limit) matches.add(JsonObject().apply { addProperty("path", f.relativeTo(root).path.replace('\\','/')); addProperty("line", idx + 1); addProperty("snippet", line.trim()) }) } } }; return ok("workspace_search_text").apply { addProperty("totalMatches", total); add("matches", matches); addProperty("truncated", total > matches.size()) }.toString() }
  private fun workspaceAnalyze(root: File): String = ok("workspace_analyze").apply { addProperty("workspace", root.absolutePath) }.toString()

  private fun resolvePath(root: File, subPath: String): File? { val rc = root.canonicalFile; val t = File(rc, subPath).canonicalFile; return if (t.path == rc.path || t.path.startsWith(rc.path + File.separator)) t else null }
  private fun isTextCandidate(name: String): Boolean = setOf("kt","java","xml","gradle","kts","properties","json","md","txt","yaml","yml").contains(name.substringAfterLast('.', ""))
  private fun ok(tool: String): JsonObject = JsonObject().apply { addProperty("ok", true); addProperty("tool", tool) }
  private fun errorJson(code: String, message: String): String = JsonObject().apply { addProperty("ok", false); addProperty("errorCode", code); addProperty("message", message) }.toString()
  private fun createToolDef(spec: ToolSpec): JsonObject = JsonObject().apply {
    addProperty("name", spec.name)
    addProperty("description", spec.desc)
    addProperty("enabled", ToolControlCenter.isEnabled(spec.name))
    add("capabilities", JsonArray().apply { spec.capabilities.forEach { add(it) } })
    addProperty("example", spec.example)
    add("inputSchema", gson.fromJson(spec.schemaJson, JsonObject::class.java))
  }
}
