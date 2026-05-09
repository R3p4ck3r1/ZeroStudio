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
 * Clangd 诊断信息(Diagnostics) 桥接转换器。
 *
 * 功能与用途：
 * 将 Clangd 异步推送过来的 `textDocument/publishDiagnostics` 通知拦截并解析为
 * AndroidIDE 所需的 `DiagnosticResult` 模型对象，最后触发 `ILanguageClient.publishDiagnostics` 更新 UI (红波浪线)。
 *
 * @author android_zero
 */
object ClangdDiagnosticsBridge {

    private val log = LoggerFactory.getLogger(ClangdDiagnosticsBridge::class.java)

    /**
     * 解析诊断推送并分发至客户端。
     */
    fun processAndPublishDiagnostics(client: ILanguageClient?, params: JSONObject) {
        if (client == null) return

        val uriString = params.optString("uri", "")
        if (uriString.isEmpty()) return

        val path = if (uriString.startsWith("file://")) uriString.substring(7) else uriString
        val targetFile = File(path)

        val diagnosticsArray = params.optJSONArray("diagnostics") ?: return
        val mappedItems = mutableListOf<DiagnosticItem>()

        for (i in 0 until diagnosticsArray.length()) {
            val diagObj = diagnosticsArray.optJSONObject(i) ?: continue

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
                severity = mapLspSeverity(severityInt)
            )

            mappedItems.add(item)
        }

        // 推送给 IDE 核心绘制波浪线
        client.publishDiagnostics(DiagnosticResult(targetFile.toPath(), mappedItems))
        log.debug("Published {} diagnostics for {}", mappedItems.size, targetFile.name)
    }

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