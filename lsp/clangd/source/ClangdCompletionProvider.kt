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

import com.itsaky.androidide.lsp.api.AbstractServiceProvider
import com.itsaky.androidide.lsp.api.ICompletionProvider
import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import java.nio.file.Path

/**
 * 针对 C/C++ 语言的代码补全提供者 (Completion Provider)。
 *
 * 功能与用途：
 * 作为 AndroidIDE 编辑器前端与 LSP 后端补全系统的桥梁。每当用户在编辑器中键入代码时，
 * AndroidIDE 的编辑器核心会调度此类，请求匹配的代码提示。
 *
 * 工作流程线路图：
 * [CodeEditor] (检测到输入或主动触发补全)
 *        |
 *        v
 * [ClangdCompletionProvider.complete] (携带行列号及上下文信息的 CompletionParams)
 *        | (校验取消状态 & 文件后缀验证)
 *        v
 * [ILanguageServerRegistry] -> 获取 "clangd-native" 服务器实例
 *        |
 *        v
 * [ClangdLanguageServer.complete] -> (将请求传递至 C++ 处理)
 *        |
 *        v
 * 返回 [CompletionResult] 给编辑器进行 UI 渲染。
 *
 * 上下文与父类关系：
 * 实现了 [ICompletionProvider] 接口以对接补全系统，继承自 [AbstractServiceProvider] 以获取 [IServerSettings] 支持。
 * 
 * @author android_zero
 */
class ClangdCompletionProvider : AbstractServiceProvider(), ICompletionProvider {

    /**
     * 判断当前提供者是否支持补全给定的文件类型。
     *
     * @param file 正在编辑的文件路径
     * @return 如果是 C/C++ 及其头文件则返回 true。
     */
    override fun canComplete(file: Path?): Boolean {
        if (file == null) return false
        if (!super.canComplete(file)) return false
        
        val ext = file.fileName.toString().substringAfterLast('.', "").lowercase()
        return ext in setOf("c", "cpp", "cc", "cxx", "h", "hpp", "hh", "hxx")
    }

    /**
     * 核心补全方法。
     * AndroidIDE 将在后台线程 (非 UI 线程) 调用此方法。
     *
     * @param params 包含当前文件 URI、光标位置、前缀文本以及取消检查器 (CancelChecker)。
     * @return 包含补全项的 [CompletionResult]。如果被取消或不支持，则返回 [CompletionResult.EMPTY]。
     */
    override fun complete(params: CompletionParams): CompletionResult {
        // 1. 及时响应用户的取消操作（例如用户继续快速打字导致之前的请求失效）
        abortCompletionIfCancelled()

        // 2. 验证文件类型
        if (!canComplete(params.file)) {
            return CompletionResult.EMPTY
        }

        // 3. 从全局注册表中寻找我们之前注册的 ClangdLanguageServer
        val server = ILanguageServerRegistry.getDefault().getServer("clangd-native") 
            ?: return CompletionResult.EMPTY

        // 4. 将补全请求委托给 ClangdLanguageServer
        // 注意：此处调用会自动应用我们在 ClangdLanguageServer 中编写的 JNI 轮询逻辑
        return server.complete(params)
    }
}