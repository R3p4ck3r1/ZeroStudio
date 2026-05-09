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
 * 负责在编辑器打开文件时发送 `didOpen`，文本修改时发送 `didChange`，关闭时发送 `didClose`。
 * 这是 LSP 服务端（Clangd）能够实时感知内存中代码变化并给出诊断信息的基石。
 *
 * @author android_zero
 */
object ClangdEditorIntegration {

    /**
     * 将给定的 [CodeEditor] 与 Clangd 语言服务器绑定，建立文档同步机制。
     *
     * @param editor 目标代码编辑器
     * @param file 当前编辑的源文件路径
     * @return 返回用于取消事件订阅的 Receipt。
     */
    @JvmStatic
    fun bindClangdLsp(editor: CodeEditor, file: Path): SubscriptionReceipt<ContentChangeEvent>? {
        val server = ILanguageServerRegistry.getDefault().getServer("clangd-native") ?: return null

        val initialContent = editor.text.toString()
        val ext = file.fileName.toString().substringAfterLast('.', "").lowercase()
        val languageId = if (ext == "c" || ext == "h") "c" else "cpp"
        
        server.didOpen(
            DidOpenTextDocumentParams(
                file = file,
                languageId = languageId,
                version = 1,
                text = initialContent
            )
        )

        var documentVersion = 1

        val receipt = editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
            documentVersion++
            
            // 采用全量同步以保证绝对一致性（对于 C++ AST 更新来说，文本量不大时全量并不会成为瓶颈）
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
     * 断开 CodeEditor 与 Clangd 语言服务器的绑定，通知服务器释放内存。
     */
    @JvmStatic
    fun unbindClangdLsp(file: Path) {
        val server = ILanguageServerRegistry.getDefault().getServer("clangd-native") ?: return
        server.didClose(DidCloseTextDocumentParams(file))
    }
}