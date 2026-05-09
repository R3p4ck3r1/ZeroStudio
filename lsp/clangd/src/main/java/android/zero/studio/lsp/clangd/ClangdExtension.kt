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

/**
 * 语言服务器的注册与初始化入口。
 *
 * 将在 AndroidIDE 的项目初始化阶段（Application 启动或 Project 加载时）被调用。
 *
 * @author android_zero
 */
object ClangdExtension {

    /**
     * 注册本地化的 ClangdLanguageServer 实例进入全局 LSP 管理器中。
     */
    @JvmStatic
    fun registerExtension() {
        val registry = ILanguageServerRegistry.getDefault()
        val server = ClangdLanguageServer()
        registry.register(server)
    }
}