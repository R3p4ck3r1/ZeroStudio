package com.wuxianggujun.tinaide.core.editorlsp

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

interface DiagnosticsManager {
    fun getDiagnostics(fileUri: String): List<Diagnostic>
    fun addListener(listener: DiagnosticsListener)
    fun removeListener(listener: DiagnosticsListener)
}

data class Diagnostic(
    val fileUri: String,
    val fileName: String,
    val line: Int,
    val column: Int,
    val endLine: Int = line,
    val endColumn: Int = column + 1,
    val message: String,
    val severity: DiagnosticSeverity,
    val source: String? = null,
    val code: String? = null
)

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO,
    HINT
}

fun interface DiagnosticsListener {
    fun onDiagnosticsChanged(fileUri: String, diagnostics: List<Diagnostic>)
}

class InMemoryDiagnosticsManager : DiagnosticsManager {
    private val diagnosticsMap = ConcurrentHashMap<String, List<Diagnostic>>()
    private val listeners = CopyOnWriteArrayList<DiagnosticsListener>()

    override fun getDiagnostics(fileUri: String): List<Diagnostic> {
        return diagnosticsMap[fileUri].orEmpty()
    }

    override fun addListener(listener: DiagnosticsListener) {
        listeners.addIfAbsent(listener)
    }

    override fun removeListener(listener: DiagnosticsListener) {
        listeners.remove(listener)
    }

    fun publishDiagnostics(fileUri: String, diagnostics: List<Diagnostic>) {
        diagnosticsMap[fileUri] = diagnostics
        listeners.forEach { it.onDiagnosticsChanged(fileUri, diagnostics) }
    }
}
