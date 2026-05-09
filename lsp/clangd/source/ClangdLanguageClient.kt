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
import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Location
import org.greenrobot.eventbus.EventBus
import java.io.File

/**
 * Clangd 语言客户端 (Language Client)。
 *
 * 功能与用途：
 * 实现了 AndroidIDE 核心的 [ILanguageClient] 接口。它是语言服务器向 IDE 前端推送信息的唯一通道。
 * 主要用途是接收底层 C++ 产生的 Diagnostics（语法报错/警告），并通过 [EventBus] 广播到对应的编辑器 UI 中进行渲染。
 * 
 * 工作流程线路图：
 * [ClangdLanguageServer] -> 解析 C++ 回调后触发 publishDiagnostics
 *        |
 *        v
 * [ClangdLanguageClient.publishDiagnostics]
 *        |
 *        v
 * [EventBus.post] 广播 [ClangdDiagnosticsEvent]
 *        |
 *        v
 * [EditorFragment] (订阅事件) -> 将波浪线渲染到 CodeEditor。
 *
 * 上下文与父类关系：
 * 实现 [ILanguageClient]。生命周期跟随 [ClangdLanguageServer]。
 * 
 * @author android_zero
 */
class ClangdLanguageClient : ILanguageClient {

    /**
     * 接收语言服务器发布的代码诊断结果，并向全局事件总线广播。
     * 
     * @param result 包含文件路径和诊断列表的结果对象。
     */
    override fun publishDiagnostics(result: DiagnosticResult?) {
        if (result == null || result === DiagnosticResult.NO_UPDATE) return
        EventBus.getDefault().post(ClangdDiagnosticsEvent(result))
    }

    override fun getDiagnosticAt(file: File?, line: Int, column: Int): DiagnosticItem? {
        // 在 AndroidIDE 架构中，单点诊断通常由 UI 层的 DiagnosticsContainer 直接处理。
        // Client 层通常不需要实现单点查询，除非有特定需求。
        return null
    }

    override fun performCodeAction(params: PerformCodeActionParams?) {
        // Clangd 自动修复 (Code Action) 预留接口，暂不实现
    }

    override fun showDocument(params: ShowDocumentParams?): ShowDocumentResult {
        // 请求前端打开某个文件并滚动到特定范围，预留接口
        return ShowDocumentResult(false)
    }

    override fun showLocations(locations: MutableList<Location>?) {
        // 请求前端显示多个位置 (如 Find References 的结果)，可发送 EventBus 事件让 UI 弹窗
    }

    override fun showMessage(params: ShowMessageParams?) {
        // 预留：接收来自 Clangd 的 Toast 提示
    }

    override fun logMessage(params: LogMessageParams?) {
        // 预留：接收来自 Clangd 的日志打印
    }
}