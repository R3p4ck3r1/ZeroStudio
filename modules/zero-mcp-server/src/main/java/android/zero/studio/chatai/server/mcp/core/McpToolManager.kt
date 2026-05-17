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

  private val specs = listOf(
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
  private val specMap = specs.associateBy { it.name }

  fun getToolDefinitions(): JsonArray = JsonArray().apply { specs.forEach { add(createToolDef(it)) } }

  fun handleCall(name: String, args: JsonObject, rootDir: File): String {
    if (!ToolControlCenter.isEnabled(name)) return errorJson("TOOL_DISABLED", "Tool is disabled: $name")
    val canonicalName = legacyAlias(name)
    return try {
      when (canonicalName) {
        "workspace_list" -> workspaceList(rootDir, args)
        "workspace_read_text" -> workspaceReadText(rootDir, args)
        "workspace_write_text" -> workspaceWriteText(rootDir, args)
        "workspace_search_text" -> workspaceSearchText(rootDir, args)
        "workspace_analyze" -> workspaceAnalyze(rootDir)
        "project_modules_list" -> projectModulesList(rootDir)
        "gradle_task_list" -> runCommand(rootDir, listOf("./gradlew", "tasks", "--all"), canonicalName)
        "gradle_task_run" -> runCommand(rootDir, listOf("./gradlew", requireArg(args, "task")!!), canonicalName)
        "shell_execute" -> runCommand(rootDir, listOf("sh", "-c", requireArg(args, "command")!!), canonicalName)
        "git_status" -> runCommand(rootDir, listOf("git", "status", "--short", "--branch"), canonicalName)
        "git_diff" -> runCommand(rootDir, listOf("git", "diff"), canonicalName)
        "test_unit_run" -> runCommand(rootDir, listOf("./gradlew", "test"), canonicalName)
        "quality_lint_run" -> runCommand(rootDir, listOf("./gradlew", "lint"), canonicalName)
        "diagnostics_file_get" -> workspaceReadText(rootDir, args)
        "android_manifest_inspect" -> androidManifestInspect(rootDir, args)
        "system_tools_list" -> systemToolsList()
        "system_tools_enable" -> systemToolsToggle(args, true)
        "system_tools_disable" -> systemToolsToggle(args, false)
        "system_tool_info" -> systemToolInfo(args)
        else -> errorJson("UNKNOWN_TOOL", "Unknown tool '$name'")
      }
    } catch (e: IllegalArgumentException) {
      errorJson("INVALID_ARGUMENT", e.message ?: "Invalid arguments")
    } catch (e: Exception) {
      errorJson("TOOL_EXECUTION_ERROR", "${e.javaClass.simpleName}: ${e.message}")
    }
  }

  private fun requireArg(args: JsonObject, key: String): String? = args.get(key)?.asString ?: throw IllegalArgumentException("'$key' is required")

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
    val file = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!file.exists()) return errorJson("FILE_NOT_FOUND", "Manifest not found: $path")
    val txt = file.readText(StandardCharsets.UTF_8)
    return ok("android_manifest_inspect").apply {
      addProperty("path", path)
      addProperty("hasApplication", txt.contains("<application"))
      addProperty("permissionCount", "<uses-permission".toRegex().findAll(txt).count())
      addProperty("activityCount", "<activity".toRegex().findAll(txt).count())
      addProperty("serviceCount", "<service".toRegex().findAll(txt).count())
    }.toString()
  }

  private fun systemToolsList(): String = ok("system_tools_list").apply {
    val arr = JsonArray()
    specs.forEach { arr.add(createToolDef(it)) }
    add("tools", arr)
  }.toString()

  private fun systemToolsToggle(args: JsonObject, enabled: Boolean): String {
    val toolName = requireArg(args, "toolName")!!
    ToolControlCenter.setEnabled(toolName, enabled)
    return ok(if (enabled) "system_tools_enable" else "system_tools_disable").apply {
      addProperty("toolName", toolName)
      addProperty("enabled", enabled)
    }.toString()
  }

  private fun systemToolInfo(args: JsonObject): String {
    val toolName = requireArg(args, "toolName")!!
    val spec = specMap[toolName] ?: return errorJson("NOT_FOUND", "Unknown tool spec: $toolName")
    return ok("system_tool_info").apply { add("tool", createToolDef(spec)) }.toString()
  }

  private fun runCommand(root: File, command: List<String>, tool: String): String {
    val process = ProcessBuilder(command).directory(root).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    return ok(tool).apply {
      addProperty("command", command.joinToString(" "))
      addProperty("exitCode", exitCode)
      addProperty("output", output.take(200000))
    }.toString()
  }

  private fun workspaceList(root: File, args: JsonObject): String {
    val subPath = args.get("path")?.asString ?: "."
    val recursive = args.get("recursive")?.asBoolean ?: false
    val directoriesOnly = args.get("directoriesOnly")?.asBoolean ?: false
    val limit = (args.get("limit")?.asInt ?: DEFAULT_LIST_LIMIT).coerceIn(1, 1000)
    val target = resolvePath(root, subPath) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!target.exists()) return errorJson("PATH_NOT_FOUND", "Path not found: $subPath")

    val entries = JsonArray(); var total = 0
    val seq = if (recursive) target.walkTopDown().drop(1) else (target.listFiles()?.asSequence() ?: emptySequence())
    seq.filter { !ignoredDirNames.contains(it.name) }.forEach { f ->
      if (directoriesOnly && !f.isDirectory) return@forEach
      total++
      if (entries.size() < limit) {
        entries.add(JsonObject().apply {
          addProperty("path", f.relativeTo(root).path.replace('\\', '/'))
          addProperty("type", if (f.isDirectory) "directory" else "file")
          if (f.isFile) addProperty("sizeBytes", f.length())
        })
      }
    }
    return ok("workspace_list").apply {
      addProperty("totalEntries", total)
      addProperty("returnedEntries", entries.size())
      addProperty("truncated", total > entries.size())
      add("entries", entries)
    }.toString()
  }

  private fun workspaceReadText(root: File, args: JsonObject): String {
    val path = requireArg(args, "path")!!
    val file = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!file.exists()) return errorJson("FILE_NOT_FOUND", "File not found: $path")
    if (!file.isFile) return errorJson("INVALID_TARGET", "Target is not a file: $path")
    if (file.length() > MAX_READ_SIZE_BYTES) return errorJson("FILE_TOO_LARGE", "File exceeds ${MAX_READ_SIZE_BYTES} bytes")

    val all = file.readLines(StandardCharsets.UTF_8)
    val start = (args.get("startLine")?.asInt ?: 1).coerceAtLeast(1)
    val end = (args.get("endLine")?.asInt ?: all.size).coerceAtLeast(start).coerceAtMost(all.size.coerceAtLeast(1))
    val selected = if (all.isEmpty()) emptyList() else all.subList(start - 1, end)

    return ok("workspace_read_text").apply {
      addProperty("path", path)
      addProperty("totalLines", all.size)
      addProperty("content", selected.joinToString("\n"))
    }.toString()
  }

  private fun workspaceWriteText(root: File, args: JsonObject): String {
    val path = requireArg(args, "path")!!
    val content = requireArg(args, "content")!!
    val append = args.get("append")?.asBoolean ?: false
    val file = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    file.parentFile?.mkdirs()
    if (append) file.appendText(content, StandardCharsets.UTF_8) else file.writeText(content, StandardCharsets.UTF_8)
    return ok("workspace_write_text").apply {
      addProperty("path", path)
      addProperty("mode", if (append) "append" else "overwrite")
      addProperty("finalSizeBytes", file.length())
    }.toString()
  }

  private fun workspaceSearchText(root: File, args: JsonObject): String {
    val keyword = requireArg(args, "keyword")!!
    val subPath = args.get("path")?.asString ?: "."
    val limit = (args.get("limit")?.asInt ?: DEFAULT_SEARCH_LIMIT).coerceIn(1, 1000)
    val target = resolvePath(root, subPath) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!target.exists()) return errorJson("PATH_NOT_FOUND", "Path not found: $subPath")

    var total = 0
    val matches = JsonArray()
    val files = if (target.isFile) sequenceOf(target) else target.walkTopDown().asSequence().filter { it.isFile }
    files.filter { isTextCandidate(it.name) }.forEach { f ->
      runCatching { f.readLines(StandardCharsets.UTF_8) }.getOrNull()?.forEachIndexed { index, line ->
        if (line.contains(keyword, ignoreCase = true)) {
          total++
          if (matches.size() < limit) {
            matches.add(JsonObject().apply {
              addProperty("path", f.relativeTo(root).path.replace('\\', '/'))
              addProperty("line", index + 1)
              addProperty("snippet", line.trim())
            })
          }
        }
      }
    }

    return ok("workspace_search_text").apply {
      addProperty("totalMatches", total)
      addProperty("truncated", total > matches.size())
      add("matches", matches)
    }.toString()
  }

  private fun workspaceAnalyze(root: File): String = ok("workspace_analyze").apply {
    addProperty("workspace", root.absolutePath)
    addProperty("existsSettingsGradleKts", File(root, "settings.gradle.kts").exists())
    addProperty("existsSettingsGradle", File(root, "settings.gradle").exists())
  }.toString()

  private fun resolvePath(root: File, subPath: String): File? {
    val canonicalRoot = root.canonicalFile
    val target = File(canonicalRoot, subPath).canonicalFile
    return if (target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) target else null
  }

  private fun isTextCandidate(name: String): Boolean = setOf("kt", "java", "xml", "gradle", "kts", "properties", "json", "md", "txt", "yaml", "yml").contains(name.substringAfterLast('.', ""))

  private fun ok(tool: String): JsonObject = JsonObject().apply {
    addProperty("ok", true)
    addProperty("tool", tool)
  }

  private fun errorJson(code: String, message: String): String = JsonObject().apply {
    addProperty("ok", false)
    addProperty("errorCode", code)
    addProperty("message", message)
  }.toString()

  private fun createToolDef(spec: ToolSpec): JsonObject = JsonObject().apply {
    addProperty("name", spec.name)
    addProperty("description", spec.desc)
    addProperty("enabled", ToolControlCenter.isEnabled(spec.name))
    add("capabilities", JsonArray().apply { spec.capabilities.forEach { add(it) } })
    addProperty("example", spec.example)
    add("inputSchema", gson.fromJson(spec.schemaJson, JsonObject::class.java))
  }

  private fun legacyAlias(name: String): String = when (name) {
    "ls" -> "workspace_list"
    "read_file" -> "workspace_read_text"
    "write_file" -> "workspace_write_text"
    "search_code" -> "workspace_search_text"
    "get_project_structure" -> "workspace_analyze"
    else -> name
  }
}
