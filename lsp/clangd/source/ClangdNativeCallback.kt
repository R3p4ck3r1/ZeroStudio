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

import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.models.DiagnosticItem
import com.itsaky.androidide.lsp.models.DiagnosticResult
import com.itsaky.androidide.lsp.models.DiagnosticSeverity
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Paths

/**
 * 接收来自 C++ 底层的 LSP 回调通知，并将其路由到 AndroidIDE 的 [ILanguageClient]。
 * 
 * 功能与用途：
 * 语言服务器通常会有服务端主动向客户端推送消息的行为，例如最重要的 `textDocument/publishDiagnostics` (语法分析报错)。
 * 本单例类负责提供 `@JvmStatic` 方法供 C++/JNI 层调用，并在内部将 JSON 解析为 AndroidIDE 模型，
 * 最后通过关联的 [ILanguageClient.publishDiagnostics] 在 UI 上渲染波浪线。
 *
 * 工作流程线路图：
 * [clangd_server.cpp (检测到代码有报错)] 
 *        |
 *        v
 * [JNI 调用 handleNativeDiagnostics(fileUri, jsonString)]
 *        |
 *        v
 * [ClangdNativeCallback] (反序列化 JSON 到 List<DiagnosticItem>)
 *        |
 *        v
 * [ILanguageClient.publishDiagnostics] (AndroidIDE 框架执行 UI 渲染)
 * 
 * 上下文关系：
 * 这是一个完全的桥接工具对象，和 [ClangdLanguageServer] 绑定生命周期。
 *
 * @author android_zero
 */
object ClangdNativeCallback {

    @Volatile
    private var languageClient: ILanguageClient? = null

    /**
     * 绑定当前的 [ILanguageClient]。由 Server 在 `connectClient` 时调用。
     */
    fun attachClient(client: ILanguageClient?) {
        this.languageClient = client
    }

    fun detachClient() {
        this.languageClient = null
    }

    /**
     * 接收到底层的 Diagnostic 通知 (代码报错/警告)。
     * 注意：C++ 侧需要调用这个方法，并传递 JSON 字符串。
     *
     * @param fileUri 产生诊断信息的源文件 URI。
     * @param jsonStr Diagnostics 的 JSON 数组结构字符串。
     */
    @JvmStatic
    fun handleNativeDiagnostics(fileUri: String, jsonStr: String) {
        val client = languageClient ?: return
        if (fileUri.isEmpty() || jsonStr.isEmpty()) return

        val filePath = if (fileUri.startsWith("file://")) fileUri.substring(7) else fileUri
        val path = Paths.get(filePath)

        val items = mutableListOf<DiagnosticItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val diagObj = jsonArray.optJSONObject(i) ?: continue
                
                val message = diagObj.optString("message", "Unknown error")
                val source = diagObj.optString("source", "clangd")
                val code = diagObj.optString("code", "")
                val severityInt = diagObj.optInt("severity", 1)
                
                val rangeObj = diagObj.optJSONObject("range") ?: continue
                val startObj = rangeObj.optJSONObject("start") ?: continue
                val endObj = rangeObj.optJSONObject("end") ?: continue
                
                val range = Range(
                    Position(startObj.optInt("line", 0), startObj.optInt("character", 0)),
                    Position(endObj.optInt("line", 0), endObj.optInt("character", 0))
                )
                
                val item = DiagnosticItem(
                    message = message,
                    code = code,
                    range = range,
                    source = source,
                    severity = mapDiagnosticSeverity(severityInt),
                    tags = emptyList() // 若需标记如 Unnecessary/Deprecated，可在此解析
                )
                
                items.add(item)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 推送给 AndroidIDE 核心渲染
        client.publishDiagnostics(DiagnosticResult(path, items))
    }

    /**
     * 映射 LSP 中的 Diagnostics 严重等级到 AndroidIDE 内部枚举。
     */
    private fun mapDiagnosticSeverity(severity: Int): DiagnosticSeverity {
        return when (severity) {
            1 -> DiagnosticSeverity.ERROR
            2 -> DiagnosticSeverity.WARNING
            3 -> DiagnosticSeverity.INFO
            4 -> DiagnosticSeverity.HINT
            else -> DiagnosticSeverity.ERROR
        }
    }
}