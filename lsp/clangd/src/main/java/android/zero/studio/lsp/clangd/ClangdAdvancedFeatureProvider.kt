/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory

/**
 * Clangd 高级 LSP 特性提供者 (全功能整合版)。
 *
 * 用途与功能：
 * 统一处理 LSP 规范中较复杂的所有进阶请求：
 * 1. 语义高亮 (Semantic Tokens) - 精准 C/C++ 语法着色
 * 2. 签名帮助 (Signature Help) - 参数推导提示
 * 3. 行内提示 (Inlay Hints) - auto / 参数名 虚拟文本提示
 * 4. 变量重命名 (Rename) - 跨文件安全重命名
 * 5. 代码动作 (Code Actions) - 快速修复 (Quick Fixes) 与重构
 * 6. 文档符号 (Document Symbols) - UI 右侧的大纲树 (Outline)
 * 7. 代码格式化 (Format Code) - 自动排版
 *
 * 工作机制：
 * 将底层 JSON-RPC 响应安全解析、拆解，并完美映射为 AndroidIDE 的标准对象模型。
 *
 * @author android_zero
 */
class ClangdAdvancedFeatureProvider(private val rpcMessenger: JsonRpcMessenger) {

    companion object {
        private val log = LoggerFactory.getLogger(ClangdAdvancedFeatureProvider::class.java)
        private const val TIMEOUT_SECONDS = 3L
        private const val FORMAT_TIMEOUT_SECONDS = 5L
    }

    /**
     * 1. 获取全量语义高亮 Token (Semantic Tokens)
     */
    suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens = withContext(Dispatchers.IO) {
        val requestParams = JSONObject().apply {
            put("textDocument", JSONObject().put("uri", params.file.toUri().toString()))
        }
        try {
            val response = rpcMessenger.sendRequest("textDocument/semanticTokens/full", requestParams)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            
            val resultObj = response.optJSONObject("result") ?: return@withContext SemanticTokens()
            val dataArray = resultObj.optJSONArray("data") ?: return@withContext SemanticTokens()
            
            val dataList = mutableListOf<Int>()
            for (i in 0 until dataArray.length()) {
                dataList.add(dataArray.optInt(i))
            }
            
            SemanticTokens(data = dataList, resultId = resultObj.optString("resultId", null))
        } catch (e: Exception) {
            log.warn("SemanticTokens request failed", e)
            SemanticTokens()
        }
    }

    /**
     * 2. 获取签名帮助 (Signature Help - 函数参数提示)
     */
    suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp = withContext(Dispatchers.IO) {
        val requestParams = buildTextDocumentPositionParams(params.file.toUri().toString(), params.position)
        try {
            val response = rpcMessenger.sendRequest("textDocument/signatureHelp", requestParams)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                
            val resultObj = response.optJSONObject("result") ?: return@withContext SignatureHelp(emptyList(), 0, 0)
            val signaturesArray = resultObj.optJSONArray("signatures") ?: return@withContext SignatureHelp(emptyList(), 0, 0)
            
            val signaturesList = mutableListOf<SignatureInformation>()
            for (i in 0 until signaturesArray.length()) {
                val sigObj = signaturesArray.optJSONObject(i) ?: continue
                val label = sigObj.optString("label", "")
                val doc = parseMarkupContent(sigObj.opt("documentation"))
                
                val paramsArray = sigObj.optJSONArray("parameters")
                val paramsList = mutableListOf<ParameterInformation>()
                if (paramsArray != null) {
                    for (j in 0 until paramsArray.length()) {
                        val paramObj = paramsArray.optJSONObject(j) ?: continue
                        val paramLabel = paramObj.optString("label", "")
                        val paramDoc = parseMarkupContent(paramObj.opt("documentation"))
                        paramsList.add(ParameterInformation(paramLabel, paramDoc))
                    }
                }
                signaturesList.add(SignatureInformation(label, doc, paramsList))
            }
            
            SignatureHelp(
                signatures = signaturesList,
                activeSignature = resultObj.optInt("activeSignature", 0),
                activeParameter = resultObj.optInt("activeParameter", 0)
            )
        } catch (e: Exception) {
            log.warn("SignatureHelp request failed", e)
            SignatureHelp(emptyList(), 0, 0)
        }
    }

    /**
     * 3. 获取行内提示 (Inlay Hints - C++ auto 类型推导及参数名提示)
     */
    suspend fun inlayHints(params: InlayHintParams): List<InlayHint> = withContext(Dispatchers.IO) {
        val requestParams = JSONObject().apply {
            put("textDocument", JSONObject().put("uri", params.file.toUri().toString()))
            put("range", serializeRange(params.range))
        }
        try {
            val response = rpcMessenger.sendRequest("textDocument/inlayHint", requestParams)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                
            val resultArray = response.optJSONArray("result") ?: return@withContext emptyList()
            val hints = mutableListOf<InlayHint>()
            
            for (i in 0 until resultArray.length()) {
                val hintObj = resultArray.optJSONObject(i) ?: continue
                val posObj = hintObj.optJSONObject("position") ?: continue
                val pos = Position(posObj.optInt("line", 0), posObj.optInt("character", 0))
                
                // label 可能是 string 或 JSON Array (InlayHintLabelPart[])
                val labelData = hintObj.opt("label")
                val labelStr = if (labelData is String) labelData else if (labelData is JSONArray && labelData.length() > 0) {
                    labelData.optJSONObject(0)?.optString("value", "") ?: ""
                } else ""
                
                val kindInt = hintObj.optInt("kind", 1) // 1: Type, 2: Parameter
                val kind = if (kindInt == 2) InlayHintKind.Parameter else InlayHintKind.Type
                
                hints.add(InlayHint(pos, labelStr, kind))
            }
            hints
        } catch (e: Exception) {
            log.warn("InlayHints request failed", e)
            emptyList()
        }
    }

    /**
     * 4. 变量重命名请求 (Rename)
     */
    suspend fun rename(params: RenameParams): WorkspaceEdit = withContext(Dispatchers.IO) {
        val requestParams = buildTextDocumentPositionParams(params.file.toUri().toString(), params.position)
            .put("newName", params.newName)
            
        try {
            val response = rpcMessenger.sendRequest("textDocument/rename", requestParams)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                
            val resultObj = response.optJSONObject("result") ?: return@withContext WorkspaceEdit(emptyList())
            WorkspaceEdit(parseDocumentChanges(resultObj))
        } catch (e: Exception) {
            log.warn("Rename request failed", e)
            WorkspaceEdit(emptyList())
        }
    }

    /**
     * 5. 快速修复与代码重构 (Code Actions)
     */
    suspend fun codeActions(fileUri: String, range: Range, diagnostics: List<DiagnosticItem>): List<CodeActionItem> = withContext(Dispatchers.IO) {
        val requestParams = JSONObject().apply {
            put("textDocument", JSONObject().put("uri", fileUri))
            put("range", serializeRange(range))
            put("context", JSONObject().apply {
                val diagArray = JSONArray()
                diagnostics.forEach { d ->
                    diagArray.put(JSONObject().apply {
                        put("message", d.message)
                        put("range", serializeRange(d.range))
                        put("severity", mapSeverityToInt(d.severity))
                        put("source", d.source)
                    })
                }
                put("diagnostics", diagArray)
            })
        }
        
        try {
            val response = rpcMessenger.sendRequest("textDocument/codeAction", requestParams)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                
            val resultArray = response.optJSONArray("result") ?: return@withContext emptyList()
            val actions = mutableListOf<CodeActionItem>()
            
            for (i in 0 until resultArray.length()) {
                val actionObj = resultArray.optJSONObject(i) ?: continue
                
                val title = actionObj.optString("title", "Quick Fix")
                val kindStr = actionObj.optString("kind", "quickfix")
                val isPreferred = actionObj.optBoolean("isPreferred", false)
                
                val kind = when {
                    kindStr.startsWith("quickfix") -> CodeActionKind.QuickFix
                    kindStr.startsWith("refactor.extract") -> CodeActionKind.RefactorExtract
                    kindStr.startsWith("refactor.inline") -> CodeActionKind.RefactorInline
                    kindStr.startsWith("refactor.rewrite") -> CodeActionKind.RefactorRewrite
                    kindStr.startsWith("refactor") -> CodeActionKind.Refactor
                    kindStr.startsWith("source.organizeImports") -> CodeActionKind.SourceOrganizeImports
                    kindStr.startsWith("source.fixAll") -> CodeActionKind.SourceFixAll
                    kindStr.startsWith("source") -> CodeActionKind.Source
                    else -> CodeActionKind.None
                }
                
                val changes = parseDocumentChanges(actionObj.optJSONObject("edit"))
                var command: Command? = null
                actionObj.optJSONObject("command")?.let { c ->
                    command = Command(c.optString("title", ""), c.optString("command", ""))
                }
                
                actions.add(CodeActionItem(title, changes, kind, command, isPreferred))
            }
            actions
        } catch (e: Exception) {
            log.warn("CodeActions request failed", e)
            emptyList()
        }
    }

    /**
     * 6. 文档符号树 (Document Symbols / Outline)
     */
    suspend fun documentSymbols(fileUri: String): DocumentSymbolsResult = withContext(Dispatchers.IO) {
        val requestParams = JSONObject().apply {
            put("textDocument", JSONObject().put("uri", fileUri))
        }

        try {
            val response = rpcMessenger.sendRequest("textDocument/documentSymbol", requestParams)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val resultArray = response.optJSONArray("result") ?: return@withContext DocumentSymbolsResult()

            val symbols = mutableListOf<DocumentSymbol>()
            for (i in 0 until resultArray.length()) {
                val symObj = resultArray.optJSONObject(i) ?: continue
                symbols.add(parseDocumentSymbol(symObj))
            }
            
            DocumentSymbolsResult(symbols = symbols)
        } catch (e: Exception) {
            log.warn("Document symbols fetch failed", e)
            DocumentSymbolsResult()
        }
    }

    /**
     * 7. 格式化代码 (Formatting) - 同步方法，供 ILanguageServer.formatCode 直接调用
     */
    fun formatCodeSync(params: FormatCodeParams, fileUri: String): CodeFormatResult {
        val requestParams = JSONObject().apply {
            put("textDocument", JSONObject().put("uri", fileUri))
            put("options", JSONObject().apply {
                put("tabSize", 4)
                put("insertSpaces", true)
            })
            if (params.range != Range.NONE) {
                put("range", serializeRange(params.range))
            }
        }

        val method = if (params.range != Range.NONE) "textDocument/rangeFormatting" else "textDocument/formatting"

        return try {
            val response = rpcMessenger.sendRequest(method, requestParams).get(FORMAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val resultArray = response.optJSONArray("result") ?: return CodeFormatResult.NONE

            val textEdits = mutableListOf<TextEdit>()
            for (i in 0 until resultArray.length()) {
                val editObj = resultArray.optJSONObject(i) ?: continue
                val range = parseRange(editObj.optJSONObject("range"))
                textEdits.add(TextEdit(range, editObj.optString("newText", "")))
            }
            CodeFormatResult(isIndexed = false, edits = textEdits)
        } catch (e: Exception) {
            log.warn("Code formatting failed", e)
            CodeFormatResult.NONE
        }
    }

    // ========================================================================
    // 私有解析与序列化辅助方法
    // ========================================================================

    private fun parseDocumentChanges(editObj: JSONObject?): List<DocumentChange> {
        if (editObj == null) return emptyList()
        val changesObj = editObj.optJSONObject("changes") ?: return emptyList()
        val documentChanges = mutableListOf<DocumentChange>()
        
        val keys = changesObj.keys()
        while (keys.hasNext()) {
            val uriStr = keys.next()
            val editsArr = changesObj.optJSONArray(uriStr) ?: continue
            val filePath = if (uriStr.startsWith("file://")) uriStr.substring(7) else uriStr
            
            val textEdits = mutableListOf<TextEdit>()
            for (j in 0 until editsArr.length()) {
                val eObj = editsArr.optJSONObject(j) ?: continue
                val r = parseRange(eObj.optJSONObject("range"))
                textEdits.add(TextEdit(r, eObj.optString("newText", "")))
            }
            documentChanges.add(DocumentChange(File(filePath).toPath(), textEdits))
        }
        return documentChanges
    }

    private fun parseDocumentSymbol(obj: JSONObject): DocumentSymbol {
        val range = parseRange(obj.optJSONObject("range"))
        val selectionRange = parseRange(obj.optJSONObject("selectionRange"))
        
        val childrenArray = obj.optJSONArray("children")
        val childrenList = mutableListOf<DocumentSymbol>()
        if (childrenArray != null) {
            for (i in 0 until childrenArray.length()) {
                val child = childrenArray.optJSONObject(i)
                if (child != null) {
                    childrenList.add(parseDocumentSymbol(child))
                }
            }
        }

        return DocumentSymbol(
            name = obj.optString("name", "Unknown"),
            detail = obj.optString("detail", ""),
            kind = mapSymbolKind(obj.optInt("kind", 1)),
            range = range,
            selectionRange = selectionRange,
            children = childrenList
        )
    }

    private fun parseRange(obj: JSONObject?): Range {
        if (obj == null) return Range.NONE
        val start = obj.optJSONObject("start") ?: return Range.NONE
        val end = obj.optJSONObject("end") ?: return Range.NONE
        return Range(
            Position(start.optInt("line", 0), start.optInt("character", 0)),
            Position(end.optInt("line", 0), end.optInt("character", 0))
        )
    }

    private fun buildTextDocumentPositionParams(uri: String, position: Position): JSONObject {
        return JSONObject().apply {
            put("textDocument", JSONObject().put("uri", uri))
            put("position", JSONObject().apply {
                put("line", position.line)
                put("character", position.column)
            })
        }
    }

    private fun serializeRange(range: Range): JSONObject {
        return JSONObject().apply {
            put("start", JSONObject().put("line", range.start.line).put("character", range.start.column))
            put("end", JSONObject().put("line", range.end.line).put("character", range.end.column))
        }
    }

    private fun mapSeverityToInt(severity: DiagnosticSeverity): Int {
        return when (severity) {
            DiagnosticSeverity.ERROR -> 1
            DiagnosticSeverity.WARNING -> 2
            DiagnosticSeverity.INFO -> 3
            DiagnosticSeverity.HINT -> 4
        }
    }

    private fun parseMarkupContent(obj: Any?): MarkupContent {
        return when (obj) {
            is String -> MarkupContent(obj, MarkupKind.PLAIN)
            is JSONObject -> MarkupContent(obj.optString("value", ""), if (obj.optString("kind") == "markdown") MarkupKind.MARKDOWN else MarkupKind.PLAIN)
            is JSONArray -> MarkupContent((0 until obj.length()).mapNotNull { (obj.opt(it) as? String) ?: (obj.opt(it) as? JSONObject)?.optString("value") }.joinToString("\n"), MarkupKind.MARKDOWN)
            else -> MarkupContent("", MarkupKind.PLAIN)
        }
    }

    private fun mapSymbolKind(kind: Int): SymbolKind {
        return when (kind) {
            1 -> SymbolKind.File
            2 -> SymbolKind.Module
            3 -> SymbolKind.Namespace
            4 -> SymbolKind.Package
            5 -> SymbolKind.Class
            6 -> SymbolKind.Method
            7 -> SymbolKind.Property
            8 -> SymbolKind.Field
            9 -> SymbolKind.Constructor
            10 -> SymbolKind.Enum
            11 -> SymbolKind.Interface
            12 -> SymbolKind.Function
            13 -> SymbolKind.Variable
            14 -> SymbolKind.Constant
            15 -> SymbolKind.String
            16 -> SymbolKind.Number
            17 -> SymbolKind.Boolean
            18 -> SymbolKind.Array
            19 -> SymbolKind.Object
            20 -> SymbolKind.Key
            21 -> SymbolKind.Null
            22 -> SymbolKind.EnumMember
            23 -> SymbolKind.Struct
            24 -> SymbolKind.Event
            25 -> SymbolKind.Operator
            26 -> SymbolKind.TypeParameter
            else -> SymbolKind.Null
        }
    }
}