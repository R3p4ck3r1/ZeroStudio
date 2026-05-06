package com.wuxianggujun.tinaide.core.lsp

import android.os.Handler
import android.os.Looper
import org.eclipse.lsp4j.DiagnosticSeverity

/**
 * 将 LSP publishDiagnostics 数据桥接为 TinaIDE 可展示的诊断模型。
 *
 * 说明：该类只做"纯协议数据 -> UI 模型"映射，不依赖任意编辑器框架事件系统。
 */
class LspDiagnosticsBridge(
    private val onUpdate: (fileUri: String, diagnostics: List<Diagnostic>) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun publish(
        fileUri: String,
        fileName: String,
        data: List<org.eclipse.lsp4j.Diagnostic>
    ) {
        val mapped = data.mapNotNull { diag ->
            val range = diag.range ?: return@mapNotNull null
            val start = range.start ?: return@mapNotNull null
            val end = range.end ?: start
            val message = diag.message ?: return@mapNotNull null

            val startLine = start.line.coerceAtLeast(0)
            val startColumn = start.character.coerceAtLeast(0)
            var endLine = end.line.coerceAtLeast(0)
            var endColumn = end.character.coerceAtLeast(0)
            if (endLine < startLine || (endLine == startLine && endColumn < startColumn)) {
                endLine = startLine
                endColumn = startColumn
            }

            val severity = when (diag.severity) {
                DiagnosticSeverity.Error -> Diagnostic.Severity.ERROR
                DiagnosticSeverity.Warning -> Diagnostic.Severity.WARNING
                DiagnosticSeverity.Information -> Diagnostic.Severity.INFO
                DiagnosticSeverity.Hint -> Diagnostic.Severity.HINT
                null -> Diagnostic.Severity.INFO
            }
            val code = diag.code?.let { rawCode ->
                when {
                    rawCode.isLeft -> rawCode.left
                    rawCode.isRight -> rawCode.right?.toString()
                    else -> null
                }
            }

            Diagnostic(
                fileUri = fileUri,
                fileName = fileName,
                line = startLine,
                column = startColumn,
                endLine = endLine,
                endColumn = endColumn,
                message = message,
                severity = severity,
                source = diag.source,
                code = code
            )
        }

        mainHandler.post {
            onUpdate(fileUri, mapped)
        }
    }
}
