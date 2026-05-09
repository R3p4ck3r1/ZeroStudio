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

import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.models.DidChangeTextDocumentParams
import com.itsaky.androidide.lsp.models.DidCloseTextDocumentParams
import com.itsaky.androidide.lsp.models.DidOpenTextDocumentParams
import com.itsaky.androidide.lsp.models.TextDocumentContentChangeEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeEvent
import java.nio.file.Path

/**
 * 针对 CodeEditor 的 Clangd 集成扩展工具类。
 *
 * 功能与用途：
 * 这是联系前端 UI (CodeEditor) 和 LSP 后端 (ClangdLanguageServer) 的纽带。
 * 负责在编辑器打开文件时发送 `didOpen`，文本修改时防抖发送 `didChange`，关闭时发送 `didClose`。
 * 
 * 工作流程线路图：
 * [EditorFragment] -> `editor.bindClangdLsp(filePath)`
 *        |
 *        v
 * [ClangdEditorIntegration] -> 初始化订阅，向 Server 发送 `didOpen`
 *        | -> [SoraEditor 事件回调] -> 向 Server 发送 `didChange`
 *
 * @author android_zero
 */
object ClangdEditorIntegration {

    /**
     * 将给定的 [CodeEditor] 与 Clangd 语言服务器绑定，建立文档同步机制。
     *
     * @param editor 目标代码编辑器
     * @param file 当前编辑的源文件路径
     * @return 返回用于取消事件订阅的 Receipt，在 Fragment 销毁时需调用 unsubscribe()。
     */
    @JvmStatic
    fun bindClangdLsp(editor: CodeEditor, file: Path): SubscriptionReceipt<ContentChangeEvent>? {
        val server = ILanguageServerRegistry.getDefault().getServer("clangd-native") 
            ?: return null

        // 1. 文档打开：同步初始全部内容
        val initialContent = editor.text.toString()
        val languageId = if (file.fileName.toString().endsWith(".c")) "c" else "cpp"
        server.didOpen(
            DidOpenTextDocumentParams(
                file = file,
                languageId = languageId,
                version = 1,
                text = initialContent
            )
        )

        var documentVersion = 1

        // 2. 订阅文本改变事件：发生变化时同步内容给 Server
        // 注意：LSP 支持增量同步，但为确保状态绝对一致且性能允许，可采用全量同步。此处采用全量同步。
        val receipt = editor.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            // 如果是由重做/撤销等引发的微小变化，也可在此做过滤防抖
            documentVersion++
            
            // 构建全量更新的 Event
            val changeEvent = TextDocumentContentChangeEvent(
                range = null,
                rangeLength = null,
                text = editor.text.toString()
            )
            
            server.didChange(
                DidChangeTextDocumentParams(
                    file = file,
                    version = documentVersion,
                    contentChanges = listOf(changeEvent)
                )
            )
        }

        return receipt
    }

    /**
     * 断开 CodeEditor 与 Clangd 语言服务器的绑定。
     *
     * @param file 正在关闭的文件路径
     */
    @JvmStatic
    fun unbindClangdLsp(file: Path) {
        val server = ILanguageServerRegistry.getDefault().getServer("clangd-native") ?: return
        server.didClose(DidCloseTextDocumentParams(file))
    }
}