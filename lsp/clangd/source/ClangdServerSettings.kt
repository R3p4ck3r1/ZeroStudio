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

import com.itsaky.androidide.lsp.util.PrefBasedServerSettings

/**
 * Clangd 语言服务器配置管理器。
 *
 * 功能与用途：
 * 继承自 AndroidIDE 的 [PrefBasedServerSettings]，自动代理和读取 AndroidIDE 的全局编辑器偏好设置。
 * 它决定了当前 Clangd 语言服务器是否应该响应代码补全、格式化、代码诊断等行为。
 * 
 * 工作流程线路图：
 * [AndroidIDE 设置界面] -> (修改选项: 关闭代码补全) -> [PreferenceManager]
 *        |
 *        v
 * [ClangdServerSettings] (读取全局配置状态)
 *        |
 *        v
 * [ClangdLanguageServer] (根据状态决定是否向底层 C++ 进程发送 LSP 请求)
 *
 * 上下文与父类关系：
 * - 继承自 [PrefBasedServerSettings]，自动复用了对全局开关（例如 completionsEnabled()）的读取能力。
 * - 作为依赖被注入到 [ClangdLanguageServer] 中。
 * 
 * @author android_zero
 */
class ClangdServerSettings : PrefBasedServerSettings() {

    /**
     * 可以在此处针对 C/C++ 语言进行特定的配置覆盖。
     * 例如，如果 C++ 解析异常昂贵，可以在低端设备上强制重写诊断开关。
     * 目前保持默认，完全遵循 AndroidIDE 的全局设定。
     */

    override fun codeAnalysisEnabled(): Boolean {
        // 调用父类读取用户偏好，并可加入额外的 C/C++ 专属控制逻辑
        return super.codeAnalysisEnabled()
    }

    override fun completionsEnabled(): Boolean {
        return super.completionsEnabled()
    }
}