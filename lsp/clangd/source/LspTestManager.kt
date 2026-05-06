
package com.wuxianggujun.tinaide.testing.lsp

import android.content.Context
import com.wuxianggujun.tinaide.core.lang.CxxFileSupport
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * LSP 测试管理器
 *
 * 使用 LSP4J 标准库生成符合规范的 JSON
 * 确保生成的 JSON 与实际发送给 Clangd 的一致
 * 
 * 功能包括：
 * - 基础 LSP 请求生成
 * - 性能基准测试
 * - 批量测试套件
 * - 诊断信息解析
 * - 连接状态管理
 */
class LspTestManager(context: Context) {

    companion object {
        private const val TAG = "LspTestManager"
        private const val MAX_HISTORY_SIZE = 50
        private const val DEFAULT_BENCHMARK_ITERATIONS = 10
    }

    private val appContext: Context = context.applicationContext

    // 测试历史记录
    private val _testHistory = MutableStateFlow<List<LspTestResult>>(emptyList())
    val testHistory: StateFlow<List<LspTestResult>> = _testHistory.asStateFlow()
    
    // 性能基准测试结果
    private val _benchmarkResults = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    val benchmarkResults: StateFlow<List<BenchmarkResult>> = _benchmarkResults.asStateFlow()
    
    // 批量测试结果
    private val _batchTestResult = MutableStateFlow<BatchTestResult?>(null)
    val batchTestResult: StateFlow<BatchTestResult?> = _batchTestResult.asStateFlow()
    
    // 连接状态
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    // LSP 服务器信息
    private val _serverInfo = MutableStateFlow<LspServerInfo?>(null)
    val serverInfo: StateFlow<LspServerInfo?> = _serverInfo.asStateFlow()
    
    // 诊断信息
    private val _diagnosticInfo = MutableStateFlow<DiagnosticInfo?>(null)
    val diagnosticInfo: StateFlow<DiagnosticInfo?> = _diagnosticInfo.asStateFlow()
    
    // 当前测试进度
    private val _testProgress = MutableStateFlow(0f)
    val testProgress: StateFlow<Float> = _testProgress.asStateFlow()
    
    // 是否正在运行测试
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * 生成 LSP initialize 请求 JSON
     * 使用 LSP4J 的 InitializeParams 确保格式正确
     */
    fun generateInitializeRequest(): String {
        val params = InitializeParams().apply {
            processId = android.os.Process.myPid()

            // 客户端信息
            clientInfo = ClientInfo().apply {
                name = "TinaIDE"
                version = "1.0.0"
            }

            // 工作区文件夹（示例）
            workspaceFolders = listOf(
                WorkspaceFolder("file:///data/user/0/com.wuxianggujun.tinaide/files/projects/test", "test")
            )

            // 客户端能力声明
            capabilities = ClientCapabilities().apply {
                textDocument = TextDocumentClientCapabilities().apply {
                    // 补全能力
                    completion = CompletionCapabilities().apply {
                        completionItem = CompletionItemCapabilities().apply {
                            snippetSupport = true
                            documentationFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
                        }
                    }

                    // 悬停能力
                    hover = HoverCapabilities().apply {
                        contentFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
                    }

                    // 跳转定义能力
                    definition = DefinitionCapabilities()

                    // 调用层次能力
                    callHierarchy = CallHierarchyCapabilities().apply {
                        dynamicRegistration = true
                    }
                }
            }
        }

        // 构建完整的 JSON-RPC 请求
        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to 1,
            "method" to "initialize",
            "params" to params
        )

        return MessageJsonHandler.toString(request)
    }

    /**
     * 生成代码补全请求 JSON
     * 使用 LSP4J 的 CompletionParams 确保格式正确
     */
    fun generateCompletionRequest(params: LspTestParams): String {
        val completionParams = CompletionParams().apply {
            // 文档标识
            textDocument = TextDocumentIdentifier(params.fileUri)

            // 位置
            position = Position(params.line, params.column)

            // 补全上下文（如果有触发字符）
            if (params.triggerChar.isNotEmpty()) {
                context = CompletionContext().apply {
                    triggerKind = CompletionTriggerKind.TriggerCharacter
                    triggerCharacter = params.triggerChar
                }
            }
        }

        // 构建完整的 JSON-RPC 请求
        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to 2,
            "method" to "textDocument/completion",
            "params" to completionParams
        )

        return MessageJsonHandler.toString(request)
    }

    /**
     * 生成跳转定义请求 JSON
     * 使用 LSP4J 的 DefinitionParams 确保格式正确
     */
    fun generateDefinitionRequest(params: LspTestParams): String {
        val definitionParams = DefinitionParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }

        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to 3,
            "method" to "textDocument/definition",
            "params" to definitionParams
        )

        return MessageJsonHandler.toString(request)
    }

    /**
     * 生成悬停提示请求 JSON
     * 使用 LSP4J 的 HoverParams 确保格式正确
     */
    fun generateHoverRequest(params: LspTestParams): String {
        val hoverParams = HoverParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }

        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to 4,
            "method" to "textDocument/hover",
            "params" to hoverParams
        )

        return MessageJsonHandler.toString(request)
    }

    /**
     * 生成测试请求 JSON
     * 根据场景类型选择对应的生成方法
     */
    fun generateTestRequest(scenario: LspTestScenario, params: LspTestParams): String {
        return when (scenario) {
            // 基础测试
            LspTestScenario.INITIALIZE -> generateInitializeRequest()
            LspTestScenario.COMPLETION -> generateCompletionRequest(params)
            LspTestScenario.DEFINITION -> generateDefinitionRequest(params)
            LspTestScenario.HOVER -> generateHoverRequest(params)
            
            // 跳转导航
            LspTestScenario.IMPLEMENTATION -> generateImplementationRequest(params)
            LspTestScenario.DECLARATION -> generateDeclarationRequest(params)
            LspTestScenario.TYPE_DEFINITION -> generateTypeDefinitionRequest(params)
            
            // 语义分析
            LspTestScenario.SEMANTIC_TOKENS_FULL -> generateSemanticTokensFullRequest(params)
            LspTestScenario.SEMANTIC_TOKENS_RANGE -> generateSemanticTokensRangeRequest(params)
            LspTestScenario.INLAY_HINT -> generateInlayHintRequest(params)
            LspTestScenario.SELECTION_RANGE -> generateSelectionRangeRequest(params)
            
            // 文档同步
            LspTestScenario.DID_OPEN -> generateDidOpenNotification(params)
            LspTestScenario.DID_CHANGE -> generateDidChangeNotification(params)
            LspTestScenario.DID_CLOSE -> generateDidCloseNotification(params)
            
            // 性能基准（使用相同的请求）
            LspTestScenario.BENCHMARK_COMPLETION -> generateCompletionRequest(params)
            LspTestScenario.BENCHMARK_HOVER -> generateHoverRequest(params)
            
            // 自定义
            LspTestScenario.CUSTOM -> params.customJson
        }
    }
    
    // ========== 新增跳转导航请求 ==========
    
    /**
     * 生成实现跳转请求
     */
    fun generateImplementationRequest(params: LspTestParams): String {
        val implementationParams = ImplementationParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(20, "textDocument/implementation", implementationParams)
    }
    
    /**
     * 生成声明跳转请求
     */
    fun generateDeclarationRequest(params: LspTestParams): String {
        val declarationParams = DeclarationParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(21, "textDocument/declaration", declarationParams)
    }
    
    /**
     * 生成类型定义跳转请求
     */
    fun generateTypeDefinitionRequest(params: LspTestParams): String {
        val typeDefinitionParams = TypeDefinitionParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(22, "textDocument/typeDefinition", typeDefinitionParams)
    }
    
    // ========== 新增语义分析请求 ==========
    
    /**
     * 生成语义令牌全量请求
     */
    fun generateSemanticTokensFullRequest(params: LspTestParams): String {
        val semanticTokensParams = SemanticTokensParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
        }
        return buildJsonRpcRequest(30, "textDocument/semanticTokens/full", semanticTokensParams)
    }
    
    /**
     * 生成语义令牌范围请求
     */
    fun generateSemanticTokensRangeRequest(params: LspTestParams): String {
        val semanticTokensRangeParams = SemanticTokensRangeParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            range = Range(
                Position(params.line, 0),
                Position(params.line + 20, 0)
            )
        }
        return buildJsonRpcRequest(31, "textDocument/semanticTokens/range", semanticTokensRangeParams)
    }
    
    /**
     * 生成内联提示请求
     */
    fun generateInlayHintRequest(params: LspTestParams): String {
        val inlayHintParams = InlayHintParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            range = Range(
                Position(0, 0),
                Position(params.line + 50, 0)
            )
        }
        return buildJsonRpcRequest(32, "textDocument/inlayHint", inlayHintParams)
    }
    
    /**
     * 生成选择范围请求
     */
    fun generateSelectionRangeRequest(params: LspTestParams): String {
        val selectionRangeParams = SelectionRangeParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            positions = listOf(Position(params.line, params.column))
        }
        return buildJsonRpcRequest(33, "textDocument/selectionRange", selectionRangeParams)
    }
    
    // ========== 新增文档同步通知 ==========
    
    /**
     * 生成文档打开通知
     */
    fun generateDidOpenNotification(params: LspTestParams): String {
        val didOpenParams = DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = params.fileUri
                languageId = detectLanguageId(params.fileUri)
                version = 1
                text = "// Sample content for testing\n#include <iostream>\n\nint main() {\n    return 0;\n}\n"
            }
        }
        return buildJsonRpcNotification("textDocument/didOpen", didOpenParams)
    }
    
    /**
     * 生成文档变更通知
     */
    fun generateDidChangeNotification(params: LspTestParams): String {
        val didChangeParams = DidChangeTextDocumentParams().apply {
            textDocument = VersionedTextDocumentIdentifier().apply {
                uri = params.fileUri
                version = 2
            }
            contentChanges = listOf(
                TextDocumentContentChangeEvent().apply {
                    text = "// Updated content\n#include <iostream>\n\nint main() {\n    std::cout << \"Hello\";\n    return 0;\n}\n"
                }
            )
        }
        return buildJsonRpcNotification("textDocument/didChange", didChangeParams)
    }
    
    /**
     * 生成文档关闭通知
     */
    fun generateDidCloseNotification(params: LspTestParams): String {
        val didCloseParams = DidCloseTextDocumentParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
        }
        return buildJsonRpcNotification("textDocument/didClose", didCloseParams)
    }
    
    /**
     * 检测语言 ID
     */
    private fun detectLanguageId(fileUri: String): String {
        val ext = fileUri.substringAfterLast('.', "").lowercase()
        return when {
            ext in CxxFileSupport.cxxSourceExtensions -> "cpp"
            ext == "c" -> "c"
            ext in CxxFileSupport.headerExtensions -> "cpp"
            ext == "java" -> "java"
            ext == "kt" -> "kotlin"
            ext == "py" -> "python"
            ext == "js" -> "javascript"
            ext == "ts" -> "typescript"
            else -> "plaintext"
        }
    }
    
    /**
     * 构建 JSON-RPC 通知（无 id）
     */
    private fun buildJsonRpcNotification(method: String, params: Any): String {
        val notification = mapOf(
            "jsonrpc" to "2.0",
            "method" to method,
            "params" to params
        )
        return MessageJsonHandler.toString(notification)
    }

    /**
     * 记录测试结果
     */
    fun recordTestResult(result: LspTestResult) {
        Timber.tag(TAG).d("Recorded test result: ${result.scenario.getDisplayName(appContext)} - ${if (result.success) "PASS" else "FAIL"}")

        val currentHistory = _testHistory.value.toMutableList()
        currentHistory.add(0, result) // 最新的在前面

        // 限制历史记录数量
        if (currentHistory.size > MAX_HISTORY_SIZE) {
            currentHistory.removeAt(currentHistory.lastIndex)
        }

        _testHistory.value = currentHistory
    }

    /**
     * 清空历史记录
     */
    fun clearHistory() {
        Timber.tag(TAG).d("Cleared test history")
        _testHistory.value = emptyList()
        _benchmarkResults.value = emptyList()
        _batchTestResult.value = null
    }

    /**
     * 获取历史记录数量
     */
    fun getHistoryCount(): Int = _testHistory.value.size
    
    // ========== 性能基准测试 ==========
    
    /**
     * 运行性能基准测试
     * 
     * @param scenario 测试场景
     * @param params 测试参数
     * @param iterations 迭代次数
     * @param onProgress 进度回调
     * @return 基准测试结果
     */
    suspend fun runBenchmark(
        scenario: LspTestScenario,
        params: LspTestParams,
        iterations: Int = DEFAULT_BENCHMARK_ITERATIONS,
        onProgress: (Float) -> Unit = {}
    ): BenchmarkResult = withContext(Dispatchers.Default) {
        Timber.tag(TAG).d("Starting benchmark: ${scenario.getDisplayName(appContext)}, iterations: $iterations")
        
        _isRunning.value = true
        val times = mutableListOf<Long>()
        var successCount = 0
        
        try {
            repeat(iterations) { index ->
                val progress = (index + 1).toFloat() / iterations
                _testProgress.value = progress
                onProgress(progress)
                
                val startTime = System.currentTimeMillis()
                
                // 生成请求 JSON
                val requestJson = generateTestRequest(scenario, params)
                
                // 验证 JSON 格式
                val validationResult = validateLspJson(requestJson, scenario)
                
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                if (validationResult.isFullyValid) {
                    times.add(duration)
                    successCount++
                }
                
                // 短暂延迟避免过快
                delay(10)
            }
            
            // 计算统计数据
            val sortedTimes = times.sorted()
            val result = BenchmarkResult(
                scenario = scenario,
                iterations = iterations,
                minTimeMs = sortedTimes.firstOrNull() ?: 0,
                maxTimeMs = sortedTimes.lastOrNull() ?: 0,
                avgTimeMs = if (times.isNotEmpty()) times.average() else 0.0,
                medianTimeMs = if (sortedTimes.isNotEmpty()) sortedTimes[sortedTimes.size / 2] else 0,
                p95TimeMs = if (sortedTimes.isNotEmpty()) sortedTimes[(sortedTimes.size * 0.95).toInt().coerceAtMost(sortedTimes.size - 1)] else 0,
                successRate = successCount.toDouble() / iterations
            )
            
            // 保存结果
            val currentResults = _benchmarkResults.value.toMutableList()
            currentResults.add(0, result)
            if (currentResults.size > 10) {
                currentResults.removeAt(currentResults.lastIndex)
            }
            _benchmarkResults.value = currentResults
            
            Timber.tag(TAG).d("Benchmark finished: ${result.getSummary(appContext)}")
            result
        } finally {
            _isRunning.value = false
            _testProgress.value = 0f
        }
    }
    
    // ========== 批量测试套件 ==========
    
    /**
     * 运行批量测试套件
     * 
     * @param config 测试套件配置
     * @param params 测试参数
     * @param onProgress 进度回调
     * @param onTestComplete 单个测试完成回调
     * @return 批量测试结果
     */
    suspend fun runTestSuite(
        config: TestSuiteConfig,
        params: LspTestParams,
        onProgress: (Float, String) -> Unit = { _, _ -> },
        onTestComplete: (LspTestResult) -> Unit = {}
    ): BatchTestResult = withContext(Dispatchers.Default) {
        Timber.tag(TAG).d("Starting test suite: ${config.name}, scenarios: ${config.scenarios.size}")
        
        _isRunning.value = true
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<LspTestResult>()
        var passedCount = 0
        var failedCount = 0
        var skippedCount = 0
        
        try {
            scenarioLoop@ for ((index, scenario) in config.scenarios.withIndex()) {
                val progress = (index + 1).toFloat() / config.scenarios.size
                _testProgress.value = progress
                onProgress(progress, scenario.getDisplayName(appContext))
                
                // 运行指定次数的迭代
                for (iteration in 0 until config.iterations) {
                    val testStartTime = System.currentTimeMillis()
                    
                    try {
                        // 生成请求
                        val requestJson = generateTestRequest(scenario, params)
                        
                        // 验证
                        val validationResult = validateLspJson(requestJson, scenario)
                        
                        val testEndTime = System.currentTimeMillis()
                        val duration = testEndTime - testStartTime
                        
                        val result = LspTestResult(
                            timestamp = System.currentTimeMillis(),
                            scenario = scenario,
                            requestJson = requestJson,
                            responseJson = null,
                            errorMessage = validationResult.errorMessage,
                            durationMs = duration,
                            success = validationResult.isFullyValid
                        )
                        
                        results.add(result)
                        onTestComplete(result)
                        
                        if (validationResult.isFullyValid) {
                            passedCount++
                        } else {
                            failedCount++
                            if (config.stopOnFirstFailure) {
                                Timber.tag(TAG).w("Test failed; stopping: ${scenario.getDisplayName(appContext)}")
                                break@scenarioLoop
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Test error: ${scenario.getDisplayName(appContext)}")
                        failedCount++
                        
                        val result = LspTestResult(
                            timestamp = System.currentTimeMillis(),
                            scenario = scenario,
                            requestJson = "",
                            responseJson = null,
                            errorMessage = e.message,
                            durationMs = 0,
                            success = false
                        )
                        results.add(result)
                        onTestComplete(result)
                        if (config.stopOnFirstFailure) {
                            Timber.tag(TAG).w("Test error; stopping: ${scenario.getDisplayName(appContext)}")
                            break@scenarioLoop
                        }
                    }
                    
                    // 测试间延迟
                    if (iteration < config.iterations - 1) {
                        delay(config.delayBetweenTests)
                    }
                }
            }
            
            val totalDuration = System.currentTimeMillis() - startTime
            
            val batchResult = BatchTestResult(
                totalTests = passedCount + failedCount + skippedCount,
                passedTests = passedCount,
                failedTests = failedCount,
                skippedTests = skippedCount,
                totalDurationMs = totalDuration,
                results = results
            )
            
            _batchTestResult.value = batchResult
            
            Timber.tag(TAG).d("Test suite finished: ${batchResult.getSummary(appContext)}")
            batchResult
        } finally {
            _isRunning.value = false
            _testProgress.value = 0f
        }
    }
    
    // ========== 诊断信息解析 ==========
    
    /**
     * 解析诊断信息 JSON
     */
    fun parseDiagnostics(jsonString: String): DiagnosticInfo? {
        return try {
            val json = JSONObject(jsonString)
            val params = json.optJSONObject("params") ?: return null
            
            val uri = params.optString("uri", "")
            val diagnosticsArray = params.optJSONArray("diagnostics") ?: JSONArray()
            
            var errorCount = 0
            var warningCount = 0
            var infoCount = 0
            var hintCount = 0
            val items = mutableListOf<DiagnosticItem>()
            
            for (i in 0 until diagnosticsArray.length()) {
                val diag = diagnosticsArray.getJSONObject(i)
                val severity = diag.optInt("severity", 1)
                val message = diag.optString("message", "")
                val range = diag.optJSONObject("range")
                val start = range?.optJSONObject("start")
                val line = start?.optInt("line", 0) ?: 0
                val column = start?.optInt("character", 0) ?: 0
                val source: String? = diag.optString("source").takeIf { it.isNotEmpty() }
                
                val severityEnum = when (severity) {
                    1 -> { errorCount++; DiagnosticSeverity.ERROR }
                    2 -> { warningCount++; DiagnosticSeverity.WARNING }
                    3 -> { infoCount++; DiagnosticSeverity.INFORMATION }
                    4 -> { hintCount++; DiagnosticSeverity.HINT }
                    else -> { infoCount++; DiagnosticSeverity.INFORMATION }
                }
                
                items.add(DiagnosticItem(severityEnum, message, line, column, source))
            }
            
            DiagnosticInfo(uri, errorCount, warningCount, infoCount, hintCount, items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse diagnostics")
            null
        }
    }
    
    /**
     * 更新诊断信息
     */
    fun updateDiagnostics(info: DiagnosticInfo) {
        _diagnosticInfo.value = info
    }
    
    // ========== 连接状态管理 ==========
    
    /**
     * 更新连接状态
     */
    fun updateConnectionStatus(status: ConnectionStatus) {
        Timber.tag(TAG).d("Connection status updated: ${status.getDisplayName(appContext)}")
        _connectionStatus.value = status
    }
    
    /**
     * 更新服务器信息
     */
    fun updateServerInfo(info: LspServerInfo) {
        Timber.tag(TAG).d("Server info updated: ${info.name} ${info.version}")
        _serverInfo.value = info
    }
    
    /**
     * 解析 initialize 响应获取服务器信息
     */
    fun parseInitializeResponse(jsonString: String): LspServerInfo? {
        return try {
            val json = JSONObject(jsonString)
            val result = json.optJSONObject("result") ?: return null
            val serverInfo = result.optJSONObject("serverInfo")
            val capabilities = result.optJSONObject("capabilities")
            
            val capabilityList = mutableListOf<String>()
            capabilities?.keys()?.forEach { key ->
                val value = capabilities.opt(key)
                if (value != null && value != false && value != JSONObject.NULL) {
                    capabilityList.add(key)
                }
            }
            
            LspServerInfo(
                name = serverInfo?.optString("name", "Unknown") ?: "Unknown",
                version = serverInfo?.optString("version"),
                capabilities = capabilityList,
                connectionStatus = ConnectionStatus.CONNECTED
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse initialize response")
            null
        }
    }
    
    // ========== 测试报告导出 ==========
    
    /**
     * 生成测试报告 JSON
     */
    fun generateTestReport(): String {
        val report = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "testHistory" to _testHistory.value.map { result ->
                mapOf(
                    "scenario" to result.scenario.getDisplayName(appContext),
                    "success" to result.success,
                    "durationMs" to result.durationMs,
                    "timestamp" to result.timestamp,
                    "error" to result.errorMessage
                )
            },
            "benchmarkResults" to _benchmarkResults.value.map { result ->
                mapOf(
                    "scenario" to result.scenario.getDisplayName(appContext),
                    "iterations" to result.iterations,
                    "avgTimeMs" to result.avgTimeMs,
                    "minTimeMs" to result.minTimeMs,
                    "maxTimeMs" to result.maxTimeMs,
                    "p95TimeMs" to result.p95TimeMs,
                    "successRate" to result.successRate
                )
            },
            "batchTestResult" to _batchTestResult.value?.let { result ->
                mapOf(
                    "totalTests" to result.totalTests,
                    "passedTests" to result.passedTests,
                    "failedTests" to result.failedTests,
                    "successRate" to result.successRate,
                    "totalDurationMs" to result.totalDurationMs
                )
            },
            "connectionStatus" to _connectionStatus.value.getDisplayName(appContext),
            "serverInfo" to _serverInfo.value?.let { info ->
                mapOf(
                    "name" to info.name,
                    "version" to info.version,
                    "capabilities" to info.capabilities
                )
            }
        )
        
        return MessageJsonHandler.toString(report)
    }
    
    /**
     * 生成测试报告文本
     */
    fun generateTestReportText(): String {
        return buildString {
            appendLine("========================================")
            appendLine(Strings.lsp_test_report_title.strOr(appContext))
            appendLine("========================================")
            appendLine()
            appendLine(
                Strings.lsp_test_report_generated_at.strOr(
                    appContext,
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date())
                )
            )
            appendLine()
            
            // 连接状态
            appendLine(Strings.lsp_test_report_section_connection_status.strOr(appContext))
            appendLine(
                Strings.lsp_test_report_status_line.strOr(
                    appContext,
                    _connectionStatus.value.getDisplayName(appContext)
                )
            )
            _serverInfo.value?.let { info ->
                appendLine(Strings.lsp_test_report_server_line.strOr(appContext, info.name, info.version ?: ""))
                appendLine(Strings.lsp_test_report_capabilities_line.strOr(appContext, info.capabilities.size))
            }
            appendLine()
            
            // 测试历史统计
            appendLine(Strings.lsp_test_report_section_history.strOr(appContext))
            val history = _testHistory.value
            val successCount = history.count { it.success }
            appendLine(Strings.batch_test_total.strOr(appContext, history.size))
            appendLine(Strings.batch_test_passed.strOr(appContext, successCount))
            appendLine(Strings.batch_test_failed.strOr(appContext, history.size - successCount))
            if (history.isNotEmpty()) {
                appendLine(
                    Strings.test_success_rate.strOr(
                        appContext,
                        String.format("%.1f", successCount.toDouble() / history.size * 100)
                    )
                )
            }
            appendLine()
            
            // 性能基准结果
            if (_benchmarkResults.value.isNotEmpty()) {
                appendLine(Strings.lsp_test_report_section_benchmark.strOr(appContext))
                _benchmarkResults.value.forEach { result ->
                    appendLine("- ${result.scenario.getDisplayName(appContext)}")
                    appendLine(Strings.test_avg_time.strOr(appContext, String.format("%.2f", result.avgTimeMs)))
                    appendLine(Strings.test_p95_time.strOr(appContext, result.p95TimeMs))
                    appendLine(Strings.test_success_rate.strOr(appContext, String.format("%.1f", result.successRate * 100)))
                }
                appendLine()
            }
            
            // 批量测试结果
            _batchTestResult.value?.let { result ->
                appendLine(Strings.lsp_test_report_section_batch.strOr(appContext))
                appendLine(result.getSummary(appContext))
            }
            
            appendLine("========================================")
        }
    }

    /**
     * 格式化 JSON 字符串
     */
    fun formatJson(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            json.toString(2)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to format JSON")
            jsonString
        }
    }

    /**
     * 验证 JSON 格式
     */
    fun isValidJson(jsonString: String): Boolean {
        return try {
            JSONObject(jsonString)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * LSP JSON 验证结果
     */
    data class LspValidationResult(
        val isValidJson: Boolean,
        val hasJsonRpc: Boolean,
        val hasId: Boolean,
        val hasMethod: Boolean,
        val hasParams: Boolean,
        val methodMatches: Boolean,
        val errorMessage: String? = null
    ) {
        val isFullyValid: Boolean
            get() = isValidJson && hasJsonRpc && hasMethod && methodMatches
    }

    /**
     * 验证 LSP JSON 请求格式
     *
     * 检查项目：
     * 1. JSON 格式是否有效
     * 2. 是否包含 jsonrpc 字段（值应为 "2.0"）
     * 3. 是否包含 id 字段（请求需要，通知不需要）
     * 4. 是否包含 method 字段
     * 5. 是否包含 params 字段
     * 6. method 值是否与预期匹配
     */
    fun validateLspJson(jsonString: String, scenario: LspTestScenario): LspValidationResult {
        Timber.tag(TAG).d("Validating LSP JSON: scenario=${scenario.getDisplayName(appContext)}")
        
        // 1. 检查 JSON 格式
        val jsonObject = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Invalid JSON format")
            return LspValidationResult(
                isValidJson = false,
                hasJsonRpc = false,
                hasId = false,
                hasMethod = false,
                hasParams = false,
                methodMatches = false,
                errorMessage = Strings.lsp_validation_invalid_json_with_message.strOr(
                    appContext,
                    e.message ?: ""
                )
            )
        }
        
        // 2. 检查 jsonrpc 字段
        val hasJsonRpc = jsonObject.has("jsonrpc")
        val jsonRpcValue = jsonObject.optString("jsonrpc", "")
        if (hasJsonRpc && jsonRpcValue != "2.0") {
            Timber.tag(TAG).w("Invalid jsonrpc value: $jsonRpcValue (expected 2.0)")
        }
        
        // 3. 检查 id 字段（通知类型不需要 id）
        val hasId = jsonObject.has("id")
        val isNotification = scenario in listOf(
            LspTestScenario.DID_OPEN,
            LspTestScenario.DID_CHANGE,
            LspTestScenario.DID_CLOSE
        )
        
        // 4. 检查 method 字段
        val hasMethod = jsonObject.has("method")
        val methodValue = jsonObject.optString("method", "")
        
        // 5. 检查 params 字段
        val hasParams = jsonObject.has("params")
        
        // 6. 检查 method 值是否匹配
        val expectedMethod = scenario.method
        val methodMatches = if (expectedMethod.isEmpty()) true else methodValue == expectedMethod
        
        if (!methodMatches && hasMethod) {
            Timber.tag(TAG).w("Method mismatch: actual='$methodValue', expected='$expectedMethod'")
        }
        
        // 构建错误信息
        val errors = mutableListOf<String>()
        if (!hasJsonRpc) errors.add(Strings.lsp_validation_missing_jsonrpc.strOr(appContext))
        else if (jsonRpcValue != "2.0") errors.add(Strings.lsp_validation_jsonrpc_should_be_2_0.strOr(appContext))
        if (!isNotification && !hasId) errors.add(Strings.lsp_validation_missing_id.strOr(appContext))
        if (!hasMethod) errors.add(Strings.lsp_validation_missing_method.strOr(appContext))
        if (hasMethod && !methodMatches) errors.add(Strings.lsp_validation_method_mismatch.strOr(appContext))
        
        val result = LspValidationResult(
            isValidJson = true,
            hasJsonRpc = hasJsonRpc && jsonRpcValue == "2.0",
            hasId = hasId,
            hasMethod = hasMethod,
            hasParams = hasParams,
            methodMatches = methodMatches,
            errorMessage = if (errors.isEmpty()) null else errors.joinToString("; ")
        )
        
        Timber.tag(TAG).d("Validation result: isFullyValid=${result.isFullyValid}, errors=${result.errorMessage}")
        return result
    }

    /**
     * 根据模板类型生成 JSON 模板
     * 使用 LSP4J 标准库确保格式正确
     */
    fun generateTemplateJson(template: LspJsonTemplate, params: LspTestParams): String {
        return when (template) {
            LspJsonTemplate.REFERENCES -> generateReferencesTemplate(params)
            LspJsonTemplate.FORMATTING -> generateFormattingTemplate(params)
            LspJsonTemplate.RANGE_FORMATTING -> generateRangeFormattingTemplate(params)
            LspJsonTemplate.RENAME -> generateRenameTemplate(params)
            LspJsonTemplate.PREPARE_RENAME -> generatePrepareRenameTemplate(params)
            LspJsonTemplate.CODE_ACTION -> generateCodeActionTemplate(params)
            LspJsonTemplate.CODE_LENS -> generateCodeLensTemplate(params)
            LspJsonTemplate.DOCUMENT_SYMBOL -> generateDocumentSymbolTemplate(params)
            LspJsonTemplate.WORKSPACE_SYMBOL -> generateWorkspaceSymbolTemplate(params)
            LspJsonTemplate.DOCUMENT_HIGHLIGHT -> generateDocumentHighlightTemplate(params)
            LspJsonTemplate.SIGNATURE_HELP -> generateSignatureHelpTemplate(params)
            LspJsonTemplate.TYPE_HIERARCHY -> generateTypeHierarchyTemplate(params)
            LspJsonTemplate.CALL_HIERARCHY -> generateCallHierarchyTemplate(params)
            LspJsonTemplate.FOLDING_RANGE -> generateFoldingRangeTemplate(params)
            LspJsonTemplate.DOCUMENT_LINK -> generateDocumentLinkTemplate(params)
            LspJsonTemplate.SWITCH_SOURCE_HEADER -> generateSwitchSourceHeaderTemplate(params)
            LspJsonTemplate.AST -> generateAstTemplate(params)
            LspJsonTemplate.MEMORY_USAGE -> generateMemoryUsageTemplate()
            // 新增模板
            LspJsonTemplate.IMPLEMENTATION -> generateImplementationTemplate(params)
            LspJsonTemplate.DECLARATION -> generateDeclarationTemplate(params)
            LspJsonTemplate.TYPE_DEFINITION -> generateTypeDefinitionTemplate(params)
            LspJsonTemplate.SEMANTIC_TOKENS_FULL -> generateSemanticTokensFullTemplate(params)
            LspJsonTemplate.SEMANTIC_TOKENS_RANGE -> generateSemanticTokensRangeTemplate(params)
            LspJsonTemplate.INLAY_HINT -> generateInlayHintTemplate(params)
            LspJsonTemplate.SELECTION_RANGE -> generateSelectionRangeTemplate(params)
            LspJsonTemplate.DID_OPEN -> generateDidOpenTemplate(params)
            LspJsonTemplate.DID_CHANGE -> generateDidChangeTemplate(params)
            LspJsonTemplate.DID_CLOSE -> generateDidCloseTemplate(params)
            LspJsonTemplate.DID_SAVE -> generateDidSaveTemplate(params)
            LspJsonTemplate.SYMBOL_INFO -> generateSymbolInfoTemplate(params)
        }
    }

    // ========== 模板生成方法 ==========
    
    private fun generateImplementationTemplate(params: LspTestParams): String {
        val implementationParams = ImplementationParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(200, "textDocument/implementation", implementationParams)
    }
    
    private fun generateDeclarationTemplate(params: LspTestParams): String {
        val declarationParams = DeclarationParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(201, "textDocument/declaration", declarationParams)
    }
    
    private fun generateTypeDefinitionTemplate(params: LspTestParams): String {
        val typeDefinitionParams = TypeDefinitionParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(202, "textDocument/typeDefinition", typeDefinitionParams)
    }
    
    private fun generateSemanticTokensFullTemplate(params: LspTestParams): String {
        val semanticTokensParams = SemanticTokensParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
        }
        return buildJsonRpcRequest(203, "textDocument/semanticTokens/full", semanticTokensParams)
    }
    
    private fun generateSemanticTokensRangeTemplate(params: LspTestParams): String {
        val semanticTokensRangeParams = SemanticTokensRangeParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            range = Range(
                Position(params.line, 0),
                Position(params.line + 20, 0)
            )
        }
        return buildJsonRpcRequest(204, "textDocument/semanticTokens/range", semanticTokensRangeParams)
    }
    
    private fun generateInlayHintTemplate(params: LspTestParams): String {
        val inlayHintParams = InlayHintParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            range = Range(
                Position(0, 0),
                Position(params.line + 50, 0)
            )
        }
        return buildJsonRpcRequest(205, "textDocument/inlayHint", inlayHintParams)
    }
    
    private fun generateSelectionRangeTemplate(params: LspTestParams): String {
        val selectionRangeParams = SelectionRangeParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            positions = listOf(Position(params.line, params.column))
        }
        return buildJsonRpcRequest(206, "textDocument/selectionRange", selectionRangeParams)
    }
    
    private fun generateDidOpenTemplate(params: LspTestParams): String {
        val didOpenParams = DidOpenTextDocumentParams().apply {
            textDocument = TextDocumentItem().apply {
                uri = params.fileUri
                languageId = detectLanguageId(params.fileUri)
                version = 1
                text = "// Sample content\n"
            }
        }
        return buildJsonRpcNotification("textDocument/didOpen", didOpenParams)
    }
    
    private fun generateDidChangeTemplate(params: LspTestParams): String {
        val didChangeParams = DidChangeTextDocumentParams().apply {
            textDocument = VersionedTextDocumentIdentifier().apply {
                uri = params.fileUri
                version = 2
            }
            contentChanges = listOf(
                TextDocumentContentChangeEvent().apply {
                    text = "// Updated content\n"
                }
            )
        }
        return buildJsonRpcNotification("textDocument/didChange", didChangeParams)
    }
    
    private fun generateDidCloseTemplate(params: LspTestParams): String {
        val didCloseParams = DidCloseTextDocumentParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
        }
        return buildJsonRpcNotification("textDocument/didClose", didCloseParams)
    }
    
    private fun generateDidSaveTemplate(params: LspTestParams): String {
        val didSaveParams = DidSaveTextDocumentParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            text = null // 可选：保存时的文本内容
        }
        return buildJsonRpcNotification("textDocument/didSave", didSaveParams)
    }
    
    private fun generateSymbolInfoTemplate(params: LspTestParams): String {
        // Clangd 特有的符号信息请求
        val symbolInfoParams = mapOf(
            "textDocument" to mapOf("uri" to params.fileUri),
            "position" to mapOf("line" to params.line, "character" to params.column)
        )
        return buildJsonRpcRequest(207, "textDocument/symbolInfo", symbolInfoParams)
    }

    private fun generateReferencesTemplate(params: LspTestParams): String {
        val referenceParams = ReferenceParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
            context = ReferenceContext(true) // includeDeclaration
        }
        return buildJsonRpcRequest(100, "textDocument/references", referenceParams)
    }

    private fun generateFormattingTemplate(params: LspTestParams): String {
        val formattingParams = DocumentFormattingParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            options = FormattingOptions().apply {
                tabSize = 4
                isInsertSpaces = true
            }
        }
        return buildJsonRpcRequest(101, "textDocument/formatting", formattingParams)
    }

    private fun generateRangeFormattingTemplate(params: LspTestParams): String {
        val rangeFormattingParams = DocumentRangeFormattingParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            range = Range(
                Position(params.line, 0),
                Position(params.line + 10, 0)
            )
            options = FormattingOptions().apply {
                tabSize = 4
                isInsertSpaces = true
            }
        }
        return buildJsonRpcRequest(102, "textDocument/rangeFormatting", rangeFormattingParams)
    }

    private fun generateRenameTemplate(params: LspTestParams): String {
        val renameParams = RenameParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
            newName = "newName"
        }
        return buildJsonRpcRequest(103, "textDocument/rename", renameParams)
    }

    private fun generatePrepareRenameTemplate(params: LspTestParams): String {
        val prepareRenameParams = PrepareRenameParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(104, "textDocument/prepareRename", prepareRenameParams)
    }

    private fun generateCodeActionTemplate(params: LspTestParams): String {
        val codeActionParams = CodeActionParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            range = Range(
                Position(params.line, params.column),
                Position(params.line, params.column + 1)
            )
            context = CodeActionContext(emptyList())
        }
        return buildJsonRpcRequest(105, "textDocument/codeAction", codeActionParams)
    }

    private fun generateCodeLensTemplate(params: LspTestParams): String {
        val codeLensParams = CodeLensParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
        }
        return buildJsonRpcRequest(106, "textDocument/codeLens", codeLensParams)
    }

    private fun generateDocumentSymbolTemplate(params: LspTestParams): String {
        val documentSymbolParams = DocumentSymbolParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
        }
        return buildJsonRpcRequest(107, "textDocument/documentSymbol", documentSymbolParams)
    }

    private fun generateWorkspaceSymbolTemplate(params: LspTestParams): String {
        val workspaceSymbolParams = WorkspaceSymbolParams().apply {
            query = "main"
        }
        return buildJsonRpcRequest(108, "workspace/symbol", workspaceSymbolParams)
    }

    private fun generateDocumentHighlightTemplate(params: LspTestParams): String {
        val documentHighlightParams = DocumentHighlightParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(109, "textDocument/documentHighlight", documentHighlightParams)
    }

    private fun generateSignatureHelpTemplate(params: LspTestParams): String {
        val signatureHelpParams = SignatureHelpParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(110, "textDocument/signatureHelp", signatureHelpParams)
    }

    private fun generateTypeHierarchyTemplate(params: LspTestParams): String {
        val typeHierarchyParams = TypeHierarchyPrepareParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(111, "textDocument/prepareTypeHierarchy", typeHierarchyParams)
    }

    private fun generateCallHierarchyTemplate(params: LspTestParams): String {
        val callHierarchyParams = CallHierarchyPrepareParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
            position = Position(params.line, params.column)
        }
        return buildJsonRpcRequest(112, "textDocument/prepareCallHierarchy", callHierarchyParams)
    }

    private fun generateFoldingRangeTemplate(params: LspTestParams): String {
        val foldingRangeParams = FoldingRangeRequestParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
        }
        return buildJsonRpcRequest(113, "textDocument/foldingRange", foldingRangeParams)
    }

    private fun generateDocumentLinkTemplate(params: LspTestParams): String {
        val documentLinkParams = DocumentLinkParams().apply {
            textDocument = TextDocumentIdentifier(params.fileUri)
        }
        return buildJsonRpcRequest(114, "textDocument/documentLink", documentLinkParams)
    }

    private fun generateSwitchSourceHeaderTemplate(params: LspTestParams): String {
        // Clangd 特有的切换头文件/源文件
        val switchParams = TextDocumentIdentifier(params.fileUri)
        return buildJsonRpcRequest(115, "textDocument/switchSourceHeader", switchParams)
    }

    private fun generateAstTemplate(params: LspTestParams): String {
        // Clangd 特有的 AST 请求
        val astParams = mapOf(
            "textDocument" to mapOf("uri" to params.fileUri),
            "range" to mapOf(
                "start" to mapOf("line" to params.line, "character" to params.column),
                "end" to mapOf("line" to params.line + 5, "character" to 0)
            )
        )
        return buildJsonRpcRequest(116, "textDocument/ast", astParams)
    }

    private fun generateMemoryUsageTemplate(): String {
        // Clangd 特有的内存使用查询
        return buildJsonRpcRequest(117, "\$/memoryUsage", emptyMap<String, Any>())
    }

    /**
     * 构建 JSON-RPC 请求
     */
    private fun buildJsonRpcRequest(id: Int, method: String, params: Any): String {
        val request = mapOf(
            "jsonrpc" to "2.0",
            "id" to id,
            "method" to method,
            "params" to params
        )
        return MessageJsonHandler.toString(request)
    }
}

