package com.wuxianggujun.tinaide.testing.lsp

import android.content.Context
import androidx.annotation.StringRes
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import kotlinx.serialization.Serializable

/**
 * LSP 测试场景类型
 */
@Serializable
enum class LspTestScenario(
    val method: String,
    val category: TestCategory,
    @param:StringRes @get:StringRes val displayNameRes: Int,
) {
    // 基础测试
    INITIALIZE("initialize", TestCategory.BASIC, Strings.lsp_test_initialize),
    COMPLETION("textDocument/completion", TestCategory.BASIC, Strings.lsp_test_completion),
    DEFINITION("textDocument/definition", TestCategory.BASIC, Strings.lsp_test_definition),
    HOVER("textDocument/hover", TestCategory.BASIC, Strings.lsp_test_hover),
    
    // 跳转相关
    IMPLEMENTATION("textDocument/implementation", TestCategory.NAVIGATION, Strings.lsp_test_implementation),
    DECLARATION("textDocument/declaration", TestCategory.NAVIGATION, Strings.lsp_test_declaration),
    TYPE_DEFINITION("textDocument/typeDefinition", TestCategory.NAVIGATION, Strings.lsp_test_type_definition),
    
    // 语义分析
    SEMANTIC_TOKENS_FULL(
        "textDocument/semanticTokens/full",
        TestCategory.SEMANTIC,
        Strings.lsp_test_semantic_tokens_full,
    ),
    SEMANTIC_TOKENS_RANGE(
        "textDocument/semanticTokens/range",
        TestCategory.SEMANTIC,
        Strings.lsp_test_semantic_tokens_range,
    ),
    INLAY_HINT("textDocument/inlayHint", TestCategory.SEMANTIC, Strings.lsp_test_inlay_hint),
    SELECTION_RANGE("textDocument/selectionRange", TestCategory.SEMANTIC, Strings.lsp_test_selection_range),
    
    // 文档同步
    DID_OPEN("textDocument/didOpen", TestCategory.DOCUMENT_SYNC, Strings.lsp_test_did_open),
    DID_CHANGE("textDocument/didChange", TestCategory.DOCUMENT_SYNC, Strings.lsp_test_did_change),
    DID_CLOSE("textDocument/didClose", TestCategory.DOCUMENT_SYNC, Strings.lsp_test_did_close),
    
    // 性能测试
    BENCHMARK_COMPLETION(
        "textDocument/completion",
        TestCategory.BENCHMARK,
        Strings.lsp_test_benchmark_completion,
    ),
    BENCHMARK_HOVER("textDocument/hover", TestCategory.BENCHMARK, Strings.lsp_test_benchmark_hover),
    
    // 自定义
    CUSTOM("", TestCategory.CUSTOM, Strings.lsp_test_custom);
    
    fun getDisplayName(context: Context): String {
        return displayNameRes.strOr(context)
    }
}

/**
 * 测试类别
 */
@Serializable
enum class TestCategory(@param:StringRes @get:StringRes val displayNameRes: Int) {
    BASIC(Strings.test_category_basic),
    NAVIGATION(Strings.test_category_navigation),
    SEMANTIC(Strings.test_category_semantic),
    DOCUMENT_SYNC(Strings.test_category_document_sync),
    BENCHMARK(Strings.test_category_benchmark),
    CUSTOM(Strings.test_category_custom);
    
    fun getDisplayName(context: Context): String {
        return displayNameRes.strOr(context)
    }
}

/**
 * 测试状态
 */
@Serializable
enum class TestStatus {
    IDLE,       // 就绪
    SENDING,    // 发送中
    SUCCESS,    // 成功
    ERROR       // 错误
}

/**
 * LSP 测试结果
 *
 * @param timestamp 测试时间戳
 * @param scenario 测试场景
 * @param requestJson 请求 JSON
 * @param responseJson 响应 JSON（可能为空）
 * @param errorMessage 错误信息（可能为空）
 * @param durationMs 响应时间（毫秒）
 * @param success 是否成功
 */
@Serializable
data class LspTestResult(
    val timestamp: Long,
    val scenario: LspTestScenario,
    val requestJson: String,
    val responseJson: String?,
    val errorMessage: String?,
    val durationMs: Long,
    val success: Boolean
) {
    /**
     * 格式化时间显示
     */
    fun getFormattedTime(): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * 获取状态文本
     */
    fun getStatusText(context: Context): String {
        return if (success) {
            Strings.test_result_success.strOr(context, durationMs)
        } else {
            Strings.test_result_failed.strOr(context)
        }
    }
}

/**
 * LSP 测试参数
 *
 * @param fileUri 文件 URI
 * @param line 行号（从 0 开始）
 * @param column 列号（从 0 开始）
 * @param triggerChar 触发字符（用于补全）
 * @param customJson 自定义 JSON 消息
 */
@Serializable
data class LspTestParams(
    val fileUri: String = "",
    val line: Int = 0,
    val column: Int = 0,
    val triggerChar: String = "",
    val customJson: String = ""
)

/**
 * 性能基准测试结果
 */
@Serializable
data class BenchmarkResult(
    val scenario: LspTestScenario,
    val iterations: Int,
    val minTimeMs: Long,
    val maxTimeMs: Long,
    val avgTimeMs: Double,
    val medianTimeMs: Long,
    val p95TimeMs: Long,
    val successRate: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getSummary(context: Context): String {
        return buildString {
            appendLine(Strings.test_scenario.strOr(context, scenario.getDisplayName(context)))
            appendLine(Strings.test_iterations.strOr(context, iterations))
            appendLine(Strings.test_min_time.strOr(context, minTimeMs))
            appendLine(Strings.test_max_time.strOr(context, maxTimeMs))
            appendLine(Strings.test_avg_time.strOr(context, String.format("%.2f", avgTimeMs)))
            appendLine(Strings.test_median_time.strOr(context, medianTimeMs))
            appendLine(Strings.test_p95_time.strOr(context, p95TimeMs))
            appendLine(Strings.test_success_rate.strOr(context, String.format("%.1f", successRate * 100)))
        }
    }
}

/**
 * 批量测试结果
 */
@Serializable
data class BatchTestResult(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val skippedTests: Int,
    val totalDurationMs: Long,
    val results: List<LspTestResult>,
    val timestamp: Long = System.currentTimeMillis()
) {
    val successRate: Double
        get() = if (totalTests > 0) passedTests.toDouble() / totalTests else 0.0
    
    fun getSummary(context: Context): String {
        return buildString {
            appendLine(Strings.batch_test_report_header.strOr(context))
            appendLine(Strings.batch_test_total.strOr(context, totalTests))
            appendLine(Strings.batch_test_passed.strOr(context, passedTests))
            appendLine(Strings.batch_test_failed.strOr(context, failedTests))
            appendLine(Strings.batch_test_skipped.strOr(context, skippedTests))
            appendLine(Strings.batch_test_duration.strOr(context, totalDurationMs))
            appendLine(Strings.test_success_rate.strOr(context, String.format("%.1f", successRate * 100)))
            appendLine(Strings.batch_test_report_footer.strOr(context))
        }
    }
}

/**
 * 诊断信息
 */
@Serializable
data class DiagnosticInfo(
    val uri: String,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val hintCount: Int,
    val diagnostics: List<DiagnosticItem>
) {
    val totalCount: Int
        get() = errorCount + warningCount + infoCount + hintCount
}

/**
 * 单个诊断项
 */
@Serializable
data class DiagnosticItem(
    val severity: DiagnosticSeverity,
    val message: String,
    val line: Int,
    val column: Int,
    val source: String?
)

/**
 * 诊断严重程度
 */
@Serializable
enum class DiagnosticSeverity(val level: Int, @param:StringRes @get:StringRes val displayNameRes: Int) {
    ERROR(1, Strings.diagnostic_error),
    WARNING(2, Strings.diagnostic_warning),
    INFORMATION(3, Strings.diagnostic_information),
    HINT(4, Strings.diagnostic_hint);
    
    fun getDisplayName(context: Context): String {
        return displayNameRes.strOr(context)
    }
}

/**
 * 连接状态
 */
@Serializable
enum class ConnectionStatus(@param:StringRes @get:StringRes val displayNameRes: Int) {
    DISCONNECTED(Strings.connection_status_disconnected),
    CONNECTING(Strings.connection_status_connecting),
    CONNECTED(Strings.connection_status_connected),
    ERROR(Strings.connection_status_error),
    RECONNECTING(Strings.connection_status_reconnecting);
    
    fun getDisplayName(context: Context): String {
        return displayNameRes.strOr(context)
    }
}

/**
 * LSP 服务器信息
 */
@Serializable
data class LspServerInfo(
    val name: String,
    val version: String?,
    val capabilities: List<String>,
    val connectionStatus: ConnectionStatus,
    val uptime: Long = 0,
    val memoryUsage: Long? = null
)

/**
 * LSP JSON 模板类型
 * 提供常用 LSP 消息的模板，方便用户快速测试
 */
@Serializable
enum class LspJsonTemplate(
    val method: String,
    val category: TemplateCategory = TemplateCategory.DOCUMENT,
    @param:StringRes @get:StringRes val displayNameRes: Int,
    @param:StringRes @get:StringRes val descriptionRes: Int,
) {
    // 文档相关
    REFERENCES(
        "textDocument/references",
        TemplateCategory.DOCUMENT,
        Strings.lsp_template_references,
        Strings.lsp_template_references_desc,
    ),
    FORMATTING(
        "textDocument/formatting",
        TemplateCategory.DOCUMENT,
        Strings.lsp_template_formatting,
        Strings.lsp_template_formatting_desc,
    ),
    RANGE_FORMATTING(
        "textDocument/rangeFormatting",
        TemplateCategory.DOCUMENT,
        Strings.lsp_template_range_formatting,
        Strings.lsp_template_range_formatting_desc,
    ),
    RENAME("textDocument/rename", TemplateCategory.DOCUMENT, Strings.lsp_template_rename, Strings.lsp_template_rename_desc),
    PREPARE_RENAME(
        "textDocument/prepareRename",
        TemplateCategory.DOCUMENT,
        Strings.lsp_template_prepare_rename,
        Strings.lsp_template_prepare_rename_desc,
    ),
    
    // 代码操作
    CODE_ACTION(
        "textDocument/codeAction",
        TemplateCategory.CODE_ACTION,
        Strings.lsp_template_code_action,
        Strings.lsp_template_code_action_desc,
    ),
    CODE_LENS(
        "textDocument/codeLens",
        TemplateCategory.CODE_ACTION,
        Strings.lsp_template_code_lens,
        Strings.lsp_template_code_lens_desc,
    ),
    
    // 符号相关
    DOCUMENT_SYMBOL(
        "textDocument/documentSymbol",
        TemplateCategory.SYMBOL,
        Strings.lsp_template_document_symbol,
        Strings.lsp_template_document_symbol_desc,
    ),
    WORKSPACE_SYMBOL(
        "workspace/symbol",
        TemplateCategory.SYMBOL,
        Strings.lsp_template_workspace_symbol,
        Strings.lsp_template_workspace_symbol_desc,
    ),
    
    // 语义相关
    DOCUMENT_HIGHLIGHT(
        "textDocument/documentHighlight",
        TemplateCategory.SEMANTIC,
        Strings.lsp_template_document_highlight,
        Strings.lsp_template_document_highlight_desc,
    ),
    SIGNATURE_HELP(
        "textDocument/signatureHelp",
        TemplateCategory.SEMANTIC,
        Strings.lsp_template_signature_help,
        Strings.lsp_template_signature_help_desc,
    ),
    
    // 类型层次
    TYPE_HIERARCHY(
        "textDocument/prepareTypeHierarchy",
        TemplateCategory.HIERARCHY,
        Strings.lsp_template_type_hierarchy,
        Strings.lsp_template_type_hierarchy_desc,
    ),
    CALL_HIERARCHY(
        "textDocument/prepareCallHierarchy",
        TemplateCategory.HIERARCHY,
        Strings.lsp_template_call_hierarchy,
        Strings.lsp_template_call_hierarchy_desc,
    ),
    
    // 折叠和链接
    FOLDING_RANGE(
        "textDocument/foldingRange",
        TemplateCategory.OTHER,
        Strings.lsp_template_folding_range,
        Strings.lsp_template_folding_range_desc,
    ),
    DOCUMENT_LINK(
        "textDocument/documentLink",
        TemplateCategory.OTHER,
        Strings.lsp_template_document_link,
        Strings.lsp_template_document_link_desc,
    ),
    
    // 跳转导航
    IMPLEMENTATION(
        "textDocument/implementation",
        TemplateCategory.NAVIGATION,
        Strings.lsp_template_implementation,
        Strings.lsp_template_implementation_desc,
    ),
    DECLARATION(
        "textDocument/declaration",
        TemplateCategory.NAVIGATION,
        Strings.lsp_template_declaration,
        Strings.lsp_template_declaration_desc,
    ),
    TYPE_DEFINITION(
        "textDocument/typeDefinition",
        TemplateCategory.NAVIGATION,
        Strings.lsp_template_type_definition,
        Strings.lsp_template_type_definition_desc,
    ),
    
    // 语义令牌
    SEMANTIC_TOKENS_FULL(
        "textDocument/semanticTokens/full",
        TemplateCategory.SEMANTIC,
        Strings.lsp_template_semantic_tokens_full,
        Strings.lsp_template_semantic_tokens_full_desc,
    ),
    SEMANTIC_TOKENS_RANGE(
        "textDocument/semanticTokens/range",
        TemplateCategory.SEMANTIC,
        Strings.lsp_template_semantic_tokens_range,
        Strings.lsp_template_semantic_tokens_range_desc,
    ),
    
    // 内联提示
    INLAY_HINT(
        "textDocument/inlayHint",
        TemplateCategory.SEMANTIC,
        Strings.lsp_template_inlay_hint,
        Strings.lsp_template_inlay_hint_desc,
    ),
    
    // 选择范围
    SELECTION_RANGE(
        "textDocument/selectionRange",
        TemplateCategory.OTHER,
        Strings.lsp_template_selection_range,
        Strings.lsp_template_selection_range_desc,
    ),
    
    // 文档同步
    DID_OPEN(
        "textDocument/didOpen",
        TemplateCategory.DOCUMENT_SYNC,
        Strings.lsp_template_did_open,
        Strings.lsp_template_did_open_desc,
    ),
    DID_CHANGE(
        "textDocument/didChange",
        TemplateCategory.DOCUMENT_SYNC,
        Strings.lsp_template_did_change,
        Strings.lsp_template_did_change_desc,
    ),
    DID_CLOSE(
        "textDocument/didClose",
        TemplateCategory.DOCUMENT_SYNC,
        Strings.lsp_template_did_close,
        Strings.lsp_template_did_close_desc,
    ),
    DID_SAVE(
        "textDocument/didSave",
        TemplateCategory.DOCUMENT_SYNC,
        Strings.lsp_template_did_save,
        Strings.lsp_template_did_save_desc,
    ),
    
    // Clangd 特有
    SWITCH_SOURCE_HEADER(
        "textDocument/switchSourceHeader",
        TemplateCategory.CLANGD,
        Strings.lsp_template_switch_source_header,
        Strings.lsp_template_switch_source_header_desc,
    ),
    AST("textDocument/ast", TemplateCategory.CLANGD, Strings.lsp_template_ast, Strings.lsp_template_ast_desc),
    MEMORY_USAGE(
        "$/memoryUsage",
        TemplateCategory.CLANGD,
        Strings.lsp_template_memory_usage,
        Strings.lsp_template_memory_usage_desc,
    ),
    SYMBOL_INFO(
        "textDocument/symbolInfo",
        TemplateCategory.CLANGD,
        Strings.lsp_template_symbol_info,
        Strings.lsp_template_symbol_info_desc,
    );
    
    fun getDisplayName(context: Context): String {
        return displayNameRes.strOr(context)
    }
    
    fun getDescription(context: Context): String {
        return descriptionRes.strOr(context)
    }
}

/**
 * 模板类别
 */
@Serializable
enum class TemplateCategory(@param:StringRes @get:StringRes val displayNameRes: Int) {
    DOCUMENT(Strings.template_category_document),
    CODE_ACTION(Strings.template_category_code_action),
    SYMBOL(Strings.template_category_symbol),
    SEMANTIC(Strings.template_category_semantic),
    HIERARCHY(Strings.template_category_hierarchy),
    NAVIGATION(Strings.template_category_navigation),
    DOCUMENT_SYNC(Strings.template_category_document_sync),
    CLANGD(Strings.template_category_clangd),
    OTHER(Strings.template_category_other);
    
    fun getDisplayName(context: Context): String {
        return displayNameRes.strOr(context)
    }
}

/**
 * 测试套件配置
 */
@Serializable
data class TestSuiteConfig(
    val name: String,
    val scenarios: List<LspTestScenario> = LspTestScenario.entries.filter {
        it.category != TestCategory.CUSTOM && it.category != TestCategory.BENCHMARK
    },
    val iterations: Int = 1,
    val delayBetweenTests: Long = 100,
    val stopOnFirstFailure: Boolean = false,
    val includePerformanceMetrics: Boolean = true
) {
    fun getDisplayName(context: Context): String {
        return name
    }
}

/**
 * 预定义测试套件
 */
object PredefinedTestSuites {
    fun getBasicSuite(context: Context) = TestSuiteConfig(
        name = Strings.test_suite_basic.strOr(context),
        scenarios = listOf(
            LspTestScenario.INITIALIZE,
            LspTestScenario.COMPLETION,
            LspTestScenario.DEFINITION,
            LspTestScenario.HOVER
        )
    )
    
    fun getNavigationSuite(context: Context) = TestSuiteConfig(
        name = Strings.test_suite_navigation.strOr(context),
        scenarios = listOf(
            LspTestScenario.DEFINITION,
            LspTestScenario.IMPLEMENTATION,
            LspTestScenario.DECLARATION,
            LspTestScenario.TYPE_DEFINITION
        )
    )
    
    fun getSemanticSuite(context: Context) = TestSuiteConfig(
        name = Strings.test_suite_semantic.strOr(context),
        scenarios = listOf(
            LspTestScenario.SEMANTIC_TOKENS_FULL,
            LspTestScenario.SEMANTIC_TOKENS_RANGE,
            LspTestScenario.INLAY_HINT,
            LspTestScenario.SELECTION_RANGE
        )
    )
    
    fun getDocumentSyncSuite(context: Context) = TestSuiteConfig(
        name = Strings.test_suite_document_sync.strOr(context),
        scenarios = listOf(
            LspTestScenario.DID_OPEN,
            LspTestScenario.DID_CHANGE,
            LspTestScenario.DID_CLOSE
        )
    )
    
    fun getPerformanceSuite(context: Context) = TestSuiteConfig(
        name = Strings.test_suite_performance.strOr(context),
        scenarios = listOf(
            LspTestScenario.BENCHMARK_COMPLETION,
            LspTestScenario.BENCHMARK_HOVER
        ),
        iterations = 10,
        includePerformanceMetrics = true
    )
    
    fun getFullSuite(context: Context) = TestSuiteConfig(
        name = Strings.test_suite_full.strOr(context),
        scenarios = LspTestScenario.entries.filter {
            it.category != TestCategory.CUSTOM && it.category != TestCategory.BENCHMARK
        }
    )
    
    fun getAllSuites(context: Context): List<TestSuiteConfig> = listOf(
        getBasicSuite(context),
        getNavigationSuite(context),
        getSemanticSuite(context),
        getDocumentSyncSuite(context),
        getPerformanceSuite(context),
        getFullSuite(context)
    )
}

