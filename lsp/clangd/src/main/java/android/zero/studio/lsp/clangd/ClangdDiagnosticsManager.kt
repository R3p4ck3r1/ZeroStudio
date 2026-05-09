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
import java.io.File
import org.json.JSONObject
import org.slf4j.LoggerFactory

/**
 * 诊断信息管理器。
 *
 * 作用：
 * 从 JSON-RPC 收到 `publishDiagnostics` 后，将其反序列化为 AndroidIDE 支持的
 * 波浪线/报错数据模型 `DiagnosticResult`。
 * 将庞大的 JSON 拆解，并将错误级别准确映射给编辑器的高亮层。
 *
 * @author android_zero
 */
object ClangdDiagnosticsManager {

    private val log = LoggerFactory.getLogger(ClangdDiagnosticsManager::class.java)

    /**
     * 处理并分发诊断信息到 IDE 的前端编辑器。
     *
     * @param client LSP 客户端代理接口
     * @param params 携带诊断数据的 JSON 对象
     */
    fun processDiagnostics(client: ILanguageClient?, params: JSONObject) {
        if (client == null) return

        val uriString = params.optString("uri", "")
        if (uriString.isEmpty()) return

        val filePath = if (uriString.startsWith("file://")) uriString.substring(7) else uriString
        val targetFile = File(filePath)

        val diagnosticsArray = params.optJSONArray("diagnostics") ?: return
        val diagnosticItems = mutableListOf<DiagnosticItem>()

        for (i in 0 until diagnosticsArray.length()) {
            val diagObj = diagnosticsArray.optJSONObject(i) ?: continue

            val message = diagObj.optString("message", "Unknown error")
            val source = diagObj.optString("source", "clangd")
            
            // Code 可能是字符串也可能是数字
            val codeRaw = diagObj.opt("code")
            val codeStr = codeRaw?.toString() ?: ""

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
                code = codeStr,
                range = range,
                source = source,
                severity = mapLspSeverity(severityInt)
            )

            diagnosticItems.add(item)
        }

        // 推送给 AndroidIDE 客户端，在编辑器产生波浪线并加入到底部日志面板
        client.publishDiagnostics(DiagnosticResult(targetFile.toPath(), diagnosticItems))
        log.debug("Dispatched {} diagnostic hints for {}", diagnosticItems.size, targetFile.name)
    }

    /**
     * 将 LSP 规范的严重等级数字映射到 AndroidIDE 的枚举类型。
     */
    private fun mapLspSeverity(severity: Int): DiagnosticSeverity {
        return when (severity) {
            1 -> DiagnosticSeverity.ERROR
            2 -> DiagnosticSeverity.WARNING
            3 -> DiagnosticSeverity.INFO
            4 -> DiagnosticSeverity.HINT
            else -> DiagnosticSeverity.ERROR
        }
    }
}