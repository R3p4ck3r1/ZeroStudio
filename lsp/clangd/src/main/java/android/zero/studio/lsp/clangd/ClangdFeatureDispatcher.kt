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
import com.itsaky.androidide.models.Location
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * Clangd 进阶特性分发器。
 *
 * 用途与功能：
 * 将格式化 (Formatting)、文档符号 (Document Symbols)、代码操作 (Code Actions) 等 
 * 高级 LSP 请求委托至此类处理。
 * 它通过发送具体的 JSON-RPC Request 到 clangd，将返回结果反序列化为 AndroidIDE 的标准 model。
 *
 * 上下文关系：
 * 由 `ClangdLanguageServer` 持有，解耦核心服务类体积。
 *
 * @author android_zero
 */
class ClangdFeatureDispatcher(private val rpcMessenger: JsonRpcMessenger) {

    companion object {
        private val log = LoggerFactory.getLogger(ClangdFeatureDispatcher::class.java)
        private const val DEFAULT_TIMEOUT_SECONDS = 5L
    }

    /**
     * 处理代码格式化请求。
     */
    fun formatCode(params: FormatCodeParams, fileUri: String): CodeFormatResult {
        val requestParams = JSONObject().apply {
            put("textDocument", JSONObject().put("uri", fileUri))
            put("options", JSONObject().apply {
                put("tabSize", 4)
                put("insertSpaces", true)
            })
            // 如果传入了有效的 Range，则执行 rangeFormatting
            if (params.range != Range.NONE) {
                put("range", JSONObject().apply {
                    put("start", JSONObject().put("line", params.range.start.line).put("character", params.range.start.column))
                    put("end", JSONObject().put("line", params.range.end.line).put("character", params.range.end.column))
                })
            }
        }

        val method = if (params.range != Range.NONE) "textDocument/rangeFormatting" else "textDocument/formatting"

        return try {
            val response = rpcMessenger.sendRequest(method, requestParams).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val resultArray = response.optJSONArray("result") ?: return CodeFormatResult.NONE

            val textEdits = mutableListOf<TextEdit>()
            for (i in 0 until resultArray.length()) {
                val editObj = resultArray.optJSONObject(i) ?: continue
                val rangeObj = editObj.optJSONObject("range") ?: continue
                val start = rangeObj.optJSONObject("start") ?: continue
                val end = rangeObj.optJSONObject("end") ?: continue

                val range = Range(
                    Position(start.optInt("line", 0), start.optInt("character", 0)),
                    Position(end.optInt("line", 0), end.optInt("character", 0))
                )
                textEdits.add(TextEdit(range, editObj.optString("newText", "")))
            }

            CodeFormatResult(isIndexed = false, edits = textEdits)
        } catch (e: Exception) {
            log.warn("Code formatting failed", e)
            CodeFormatResult.NONE
        }
    }

    /**
     * 处理文档结构/符号树请求。
     */
    fun documentSymbols(fileUri: String): DocumentSymbolsResult {
        val requestParams = JSONObject().apply {
            put("textDocument", JSONObject().put("uri", fileUri))
        }

        return try {
            val response = rpcMessenger.sendRequest("textDocument/documentSymbol", requestParams).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val resultArray = response.optJSONArray("result") ?: return DocumentSymbolsResult()

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

    private fun parseDocumentSymbol(obj: JSONObject): DocumentSymbol {
        val rangeObj = obj.optJSONObject("range")
        val range = parseRange(rangeObj)
        val selectionRangeObj = obj.optJSONObject("selectionRange")
        val selectionRange = parseRange(selectionRangeObj)
        
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