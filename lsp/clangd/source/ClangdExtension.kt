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
 * Clangd 扩展注册器。
 *
 * 功能与用途：
 * 负责在 AndroidIDE 初始化阶段（或项目打开时），将自定义的 C/C++ 语言服务器 (Clangd)
 * 注册并挂载到 AndroidIDE 标准的 LSP 生态中。
 *
 * 工作流程线路图：
 * [AndroidIDE 启动 / ProjectOpenedEvent]
 *        |
 *        v
 * [ClangdExtension.initialize]
 *        | (实例化 Server 和 Settings)
 *        v
 * [ILanguageServerRegistry.register] (注册到系统)
 *        |
 *        v
 * [AndroidIDE] (系统检测到新 Server，开始监听生命周期，调用 setupWorkspace 等)
 *
 * 上下文关系：
 * 需要在 Application 的 onCreate 或 ProjectManager 初始化时调用 [ClangdExtension.initialize]。
 * 
 * @author android_zero
 */
object ClangdExtension {

    /**
     * 将 ClangdLanguageServer 注入到系统环境。
     * 推荐在应用程序启动时，或收到 `ProjectInitializedEvent` 时调用一次。
     */
    fun initialize() {
        val registry = ILanguageServerRegistry.getDefault()
        
        // 防止重复注册
        if (registry.getServer("clangd-native") != null) {
            return
        }

        // 实例化语言服务器和配置管理器
        val server = ClangdLanguageServer()
        val settings = ClangdServerSettings()
        
        // 绑定配置
        server.applySettings(settings)

        // 注册到系统的多语言中心
        registry.register(server)
    }
}