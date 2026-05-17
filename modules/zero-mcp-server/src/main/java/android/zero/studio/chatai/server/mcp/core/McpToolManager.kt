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
      ToolSpec("workspace_info", "获取文件/目录统计信息", listOf("workspace", "metadata"), "{\"path\":\"app\"}"),
      ToolSpec("workspace_rename", "重命名文件/目录", listOf("workspace", "write"), "{\"fromPath\":\"a.txt\",\"toPath\":\"b.txt\"}"),
      ToolSpec("workspace_copy", "复制文件/目录", listOf("workspace", "write"), "{\"fromPath\":\"a.txt\",\"toPath\":\"backup/a.txt\"}"),
      ToolSpec("workspace_move", "移动文件/目录", listOf("workspace", "write"), "{\"fromPath\":\"a.txt\",\"toPath\":\"docs/a.txt\"}"),
      ToolSpec("workspace_delete", "删除文件/目录", listOf("workspace", "write", "danger"), "{\"path\":\"tmp\",\"recursive\":true}"),
      ToolSpec("gradle_wrapper_info", "读取 gradle-wrapper.properties", listOf("gradle", "wrapper", "read"), "{}"),
      ToolSpec("gradle_wrapper_update", "更新 gradle-wrapper distributionUrl", listOf("gradle", "wrapper", "write"), "{\"version\":\"8.7\",\"distributionType\":\"all\"}"),
      ToolSpec("project_module_src_tree", "列出模块 src 目录树", listOf("project", "module", "src"), "{\"modulePath\":\"app\",\"limit\":300}"),
      ToolSpec("project_apk_locate", "定位模块 APK 输出", listOf("project", "apk", "read"), "{\"modulePath\":\"app\",\"variant\":\"debug\"}"),
      ToolSpec("search_workspace_text", "全工程文本检索（高级过滤）", listOf("search", "workspace"), "{\"query\":\"McpService\",\"path\":\".\",\"limit\":100}"),
      ToolSpec("logs_file_get", "读取指定日志文件内容", listOf("logs", "read"), "{\"path\":\"build.log\",\"maxLines\":400}"),
      ToolSpec("git_log", "读取 git 提交历史", listOf("git", "history"), "{\"limit\":30}"),
      ToolSpec("diagnostics_workspace_summary", "工作区基础诊断统计", listOf("diagnostics", "analysis"), "{}"),
      ToolSpec("system_health_check", "MCP 服务健康检查", listOf("system", "health"), "{}"),
      ToolSpec("system_policy_get", "获取当前工具安全策略", listOf("system", "policy"), "{}"),
      ToolSpec("system_tools_set_bulk", "批量设置工具开关", listOf("system", "tool-control", "write"), "{\"enabled\":false,\"toolNames\":[\"shell_execute\",\"workspace_delete\"]}"),
      ToolSpec("project_modules_list", "解析 settings.gradle(.kts) 输出模块列表", listOf("project", "gradle", "modules"), "{}"),
      ToolSpec("gradle_task_list", "执行 ./gradlew tasks --all", listOf("gradle", "task", "build"), "{}"),
      ToolSpec("gradle_task_run", "运行指定 gradle task", listOf("gradle", "task", "build", "execute"), "{\"task\":\":app:assembleDebug\"}"),
      ToolSpec("shell_execute", "执行 shell 命令（当前工作区）", listOf("shell", "execute"), "{\"command\":\"ls -la\"}"),
      ToolSpec("git_status", "执行 git status --short --branch", listOf("git", "status"), "{}"),
      ToolSpec("git_diff", "执行 git diff", listOf("git", "diff"), "{}"),
      ToolSpec("test_unit_run", "执行 ./gradlew test", listOf("test", "gradle"), "{}"),
      ToolSpec("quality_lint_run", "执行 ./gradlew lint", listOf("quality", "lint"), "{}"),
      ToolSpec("test_android_run", "执行 Android instrument tests", listOf("test", "android"), "{\"modulePath\":\"app\"}"),
      ToolSpec("dependency_list", "列出模块依赖树", listOf("dependency", "gradle"), "{\"modulePath\":\"app\",\"configuration\":\"implementation\"}"),
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
        "workspace_info" -> workspaceInfo(rootDir, args)
        "workspace_rename" -> workspaceRename(rootDir, args)
        "workspace_copy" -> workspaceCopy(rootDir, args)
        "workspace_move" -> workspaceMove(rootDir, args)
        "workspace_delete" -> workspaceDelete(rootDir, args)
        "project_modules_list" -> projectModulesList(rootDir)
        "project_module_src_tree" -> projectModuleSrcTree(rootDir, args)
        "project_apk_locate" -> projectApkLocate(rootDir, args)
        "search_workspace_text" -> searchWorkspaceText(rootDir, args)
        "logs_file_get" -> logsFileGet(rootDir, args)
        "git_log" -> gitLog(rootDir, args)
        "diagnostics_workspace_summary" -> diagnosticsWorkspaceSummary(rootDir)
        "system_health_check" -> systemHealthCheck(rootDir)
        "system_policy_get" -> systemPolicyGet()
        "system_tools_set_bulk" -> systemToolsSetBulk(args)
        "gradle_task_list" -> runCommand(rootDir, listOf("./gradlew", "tasks", "--all"), canonicalName)
        "gradle_task_run" -> runCommand(rootDir, listOf("./gradlew", requireArg(args, "task")!!), canonicalName)
        "gradle_wrapper_info" -> gradleWrapperInfo(rootDir)
        "gradle_wrapper_update" -> gradleWrapperUpdate(rootDir, args)
        "shell_execute" -> runCommand(rootDir, listOf("sh", "-c", requireArg(args, "command")!!), canonicalName)
        "git_status" -> runCommand(rootDir, listOf("git", "status", "--short", "--branch"), canonicalName)
        "git_diff" -> runCommand(rootDir, listOf("git", "diff"), canonicalName)
        "test_unit_run" -> runCommand(rootDir, listOf("./gradlew", "test"), canonicalName)
        "quality_lint_run" -> runCommand(rootDir, listOf("./gradlew", "lint"), canonicalName)
        "test_android_run" -> testAndroidRun(rootDir, args)
        "dependency_list" -> dependencyList(rootDir, args)
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


  private fun workspaceInfo(root: File, args: JsonObject): String {
    val path = args.get("path")?.asString ?: "."
    val target = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!target.exists()) return errorJson("PATH_NOT_FOUND", "Path not found: $path")

    var fileCount = 0
    var dirCount = 0
    var totalBytes = 0L
    if (target.isDirectory) {
      target.walkTopDown().forEach {
        if (it.isDirectory) dirCount++ else {
          fileCount++
          totalBytes += it.length()
        }
      }
    } else {
      fileCount = 1
      totalBytes = target.length()
    }

    return ok("workspace_info").apply {
      addProperty("path", target.relativeTo(root).path.ifEmpty { "." })
      addProperty("isDirectory", target.isDirectory)
      addProperty("fileCount", fileCount)
      addProperty("directoryCount", dirCount)
      addProperty("totalBytes", totalBytes)
      addProperty("lastModified", target.lastModified())
    }.toString()
  }

  private fun workspaceRename(root: File, args: JsonObject): String {
    val fromPath = requireArg(args, "fromPath")!!
    val toPath = requireArg(args, "toPath")!!
    val from = resolvePath(root, fromPath) ?: return errorJson("ACCESS_DENIED", "fromPath outside workspace")
    val to = resolvePath(root, toPath) ?: return errorJson("ACCESS_DENIED", "toPath outside workspace")
    if (!from.exists()) return errorJson("FILE_NOT_FOUND", "Source not found: $fromPath")
    to.parentFile?.mkdirs()
    val ok = from.renameTo(to)
    if (!ok) return errorJson("EXECUTION_FAILED", "Rename failed: $fromPath -> $toPath")
    return ok("workspace_rename").apply {
      addProperty("fromPath", fromPath)
      addProperty("toPath", toPath)
    }.toString()
  }

  private fun workspaceCopy(root: File, args: JsonObject): String {
    val fromPath = requireArg(args, "fromPath")!!
    val toPath = requireArg(args, "toPath")!!
    val from = resolvePath(root, fromPath) ?: return errorJson("ACCESS_DENIED", "fromPath outside workspace")
    val to = resolvePath(root, toPath) ?: return errorJson("ACCESS_DENIED", "toPath outside workspace")
    if (!from.exists()) return errorJson("FILE_NOT_FOUND", "Source not found: $fromPath")

    copyRecursively(from, to)
    return ok("workspace_copy").apply {
      addProperty("fromPath", fromPath)
      addProperty("toPath", toPath)
    }.toString()
  }

  private fun workspaceMove(root: File, args: JsonObject): String {
    val fromPath = requireArg(args, "fromPath")!!
    val toPath = requireArg(args, "toPath")!!
    val from = resolvePath(root, fromPath) ?: return errorJson("ACCESS_DENIED", "fromPath outside workspace")
    val to = resolvePath(root, toPath) ?: return errorJson("ACCESS_DENIED", "toPath outside workspace")
    if (!from.exists()) return errorJson("FILE_NOT_FOUND", "Source not found: $fromPath")

    copyRecursively(from, to)
    if (!deleteRecursivelySafe(from)) return errorJson("EXECUTION_FAILED", "Move cleanup failed at: $fromPath")
    return ok("workspace_move").apply {
      addProperty("fromPath", fromPath)
      addProperty("toPath", toPath)
    }.toString()
  }

  private fun copyRecursively(source: File, target: File) {
    if (source.isDirectory) {
      target.mkdirs()
      source.listFiles()?.forEach { child ->
        copyRecursively(child, File(target, child.name))
      }
    } else {
      target.parentFile?.mkdirs()
      source.inputStream().use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
      }
    }
  }

  private fun deleteRecursivelySafe(target: File): Boolean {
    if (!target.exists()) return true
    if (target.isDirectory) target.listFiles()?.forEach { if (!deleteRecursivelySafe(it)) return false }
    return target.delete()
  }

  private fun workspaceDelete(root: File, args: JsonObject): String {
    val path = requireArg(args, "path")!!
    val recursive = args.get("recursive")?.asBoolean ?: false
    val target = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!target.exists()) return errorJson("FILE_NOT_FOUND", "Path not found: $path")
    if (target.isDirectory && !recursive) return errorJson("INVALID_ARGUMENT", "Directory delete requires recursive=true")
    if (!deleteRecursivelySafe(target)) return errorJson("EXECUTION_FAILED", "Delete failed: $path")
    return ok("workspace_delete").apply { addProperty("path", path); addProperty("deleted", true) }.toString()
  }

  private fun gradleWrapperInfo(root: File): String {
    val file = File(root, "gradle/wrapper/gradle-wrapper.properties")
    if (!file.exists()) return errorJson("FILE_NOT_FOUND", "gradle-wrapper.properties not found")
    val text = file.readText(StandardCharsets.UTF_8)
    val url = text.lineSequence().firstOrNull { it.trim().startsWith("distributionUrl=") }?.substringAfter("=") ?: ""
    return ok("gradle_wrapper_info").apply { addProperty("distributionUrl", url); addProperty("content", text) }.toString()
  }

  private fun gradleWrapperUpdate(root: File, args: JsonObject): String {
    val version = requireArg(args, "version")!!
    val distributionType = args.get("distributionType")?.asString ?: "all"
    val file = File(root, "gradle/wrapper/gradle-wrapper.properties")
    if (!file.exists()) return errorJson("FILE_NOT_FOUND", "gradle-wrapper.properties not found")
    val lines = file.readLines(StandardCharsets.UTF_8).toMutableList()
    val idx = lines.indexOfFirst { it.trim().startsWith("distributionUrl=") }
    if (idx == -1) return errorJson("EXECUTION_FAILED", "distributionUrl not found")
    val base = "https\://services.gradle.org/distributions/"
    val newUrl = "${base}gradle-${version}-${distributionType}.zip"
    lines[idx] = "distributionUrl=$newUrl"
    file.writeText(lines.joinToString("
"), StandardCharsets.UTF_8)
    return ok("gradle_wrapper_update").apply { addProperty("distributionUrl", newUrl) }.toString()
  }

  private fun projectModuleSrcTree(root: File, args: JsonObject): String {
    val modulePath = requireArg(args, "modulePath")!!
    val limit = (args.get("limit")?.asInt ?: 300).coerceIn(1, 5000)
    val src = resolvePath(root, "$modulePath/src") ?: return errorJson("ACCESS_DENIED", "modulePath outside workspace")
    if (!src.exists()) return errorJson("PATH_NOT_FOUND", "src path not found: $modulePath/src")
    val arr = JsonArray()
    var total = 0
    src.walkTopDown().forEach {
      total++
      if (arr.size() < limit) arr.add(it.relativeTo(root).path.replace('\\','/'))
    }
    return ok("project_module_src_tree").apply { addProperty("total", total); addProperty("truncated", total > arr.size()); add("entries", arr) }.toString()
  }

  private fun projectApkLocate(root: File, args: JsonObject): String {
    val modulePath = args.get("modulePath")?.asString ?: "app"
    val variant = args.get("variant")?.asString ?: "debug"
    val apkDir = resolvePath(root, "$modulePath/build/outputs/apk/$variant") ?: return errorJson("ACCESS_DENIED", "module path outside workspace")
    if (!apkDir.exists()) return errorJson("PATH_NOT_FOUND", "APK output path not found: $modulePath/build/outputs/apk/$variant")
    val apks = apkDir.walkTopDown().filter { it.isFile && it.extension.equals("apk", true) }.toList()
    val arr = JsonArray()
    apks.forEach { arr.add(it.relativeTo(root).path.replace('\\','/')) }
    return ok("project_apk_locate").apply { addProperty("count", apks.size); add("apks", arr) }.toString()
  }

  private fun searchWorkspaceText(root: File, args: JsonObject): String {
    val query = requireArg(args, "query")!!
    val path = args.get("path")?.asString ?: "."
    val limit = (args.get("limit")?.asInt ?: 100).coerceIn(1, 5000)
    val caseSensitive = args.get("caseSensitive")?.asBoolean ?: false
    val target = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!target.exists()) return errorJson("PATH_NOT_FOUND", "Path not found: $path")

    val matches = JsonArray(); var total=0
    val files = if (target.isFile) sequenceOf(target) else target.walkTopDown().asSequence().filter { it.isFile }
    files.filter { isTextCandidate(it.name) }.forEach { f ->
      runCatching { f.readLines(StandardCharsets.UTF_8) }.getOrNull()?.forEachIndexed { idx, line ->
        val hit = if (caseSensitive) line.contains(query) else line.contains(query, true)
        if (hit) { total++; if (matches.size() < limit) matches.add(JsonObject().apply { addProperty("path", f.relativeTo(root).path.replace('\\','/')); addProperty("line", idx+1); addProperty("snippet", line.trim()) }) }
      }
    }
    return ok("search_workspace_text").apply { addProperty("totalMatches", total); addProperty("truncated", total > matches.size()); add("matches", matches) }.toString()
  }

  private fun logsFileGet(root: File, args: JsonObject): String {
    val path = requireArg(args, "path")!!
    val maxLines = (args.get("maxLines")?.asInt ?: 400).coerceIn(1, 5000)
    val file = resolvePath(root, path) ?: return errorJson("ACCESS_DENIED", "Path outside workspace")
    if (!file.exists()) return errorJson("FILE_NOT_FOUND", "File not found: $path")
    val lines = file.readLines(StandardCharsets.UTF_8)
    val out = lines.takeLast(maxLines)
    return ok("logs_file_get").apply { addProperty("path", path); addProperty("totalLines", lines.size); addProperty("returnedLines", out.size); addProperty("content", out.joinToString("
")) }.toString()
  }

  private fun gitLog(root: File, args: JsonObject): String {
    val limit = (args.get("limit")?.asInt ?: 30).coerceIn(1, 200)
    return runCommand(root, listOf("git", "log", "--oneline", "-$limit"), "git_log")
  }

  private fun diagnosticsWorkspaceSummary(root: File): String {
    var files = 0
    var kotlinFiles = 0
    var javaFiles = 0
    var xmlFiles = 0
    root.walkTopDown().forEach {
      if (it.isFile) {
        files++
        when (it.extension.lowercase()) {
          "kt", "kts" -> kotlinFiles++
          "java" -> javaFiles++
          "xml" -> xmlFiles++
        }
      }
    }
    return ok("diagnostics_workspace_summary").apply {
      addProperty("totalFiles", files)
      addProperty("kotlinFiles", kotlinFiles)
      addProperty("javaFiles", javaFiles)
      addProperty("xmlFiles", xmlFiles)
    }.toString()
  }

  private fun systemHealthCheck(root: File): String = ok("system_health_check").apply {
    addProperty("workspace", root.absolutePath)
    addProperty("toolsCount", specs.size)
    addProperty("enabledTools", specs.count { ToolControlCenter.isEnabled(it.name) })
    addProperty("disabledTools", specs.count { !ToolControlCenter.isEnabled(it.name) })
  }.toString()

  private fun systemPolicyGet(): String = ok("system_policy_get").apply {
    val dangerous = JsonArray().apply {
      add("workspace_delete")
      add("shell_execute")
      add("gradle_wrapper_update")
    }
    add("dangerousTools", dangerous)
    addProperty("workspaceBounded", true)
    addProperty("maxReadSizeBytes", MAX_READ_SIZE_BYTES)
    addProperty("defaultSearchLimit", DEFAULT_SEARCH_LIMIT)
  }.toString()

  private fun testAndroidRun(root: File, args: JsonObject): String {
    val modulePath = args.get("modulePath")?.asString ?: "app"
    return runCommand(root, listOf("./gradlew", ":$modulePath:connectedDebugAndroidTest"), "test_android_run")
  }

  private fun dependencyList(root: File, args: JsonObject): String {
    val modulePath = args.get("modulePath")?.asString ?: "app"
    val configuration = args.get("configuration")?.asString ?: "implementation"
    return runCommand(root, listOf("./gradlew", ":$modulePath:dependencies", "--configuration", configuration), "dependency_list")
  }

  private fun systemToolsSetBulk(args: JsonObject): String {
    val enabled = args.get("enabled")?.asBoolean ?: return errorJson("INVALID_ARGUMENT", "'enabled' is required")
    val namesJson = args.get("toolNames")?.asJsonArray ?: return errorJson("INVALID_ARGUMENT", "'toolNames' is required")
    val updated = JsonArray()
    for (item in namesJson) {
      val name = item.asString
      ToolControlCenter.setEnabled(name, enabled)
      updated.add(name)
    }
    return ok("system_tools_set_bulk").apply {
      addProperty("enabled", enabled)
      add("toolNames", updated)
      addProperty("count", updated.size())
    }.toString()
  }
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
