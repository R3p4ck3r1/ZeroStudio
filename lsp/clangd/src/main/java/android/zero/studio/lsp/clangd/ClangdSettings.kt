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

import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.lsp.util.PrefBasedServerSettings
import java.io.File

/**
 * Clangd 核心运行参数模型。
 * 
 * 用于构建传递给 `clangd` 二进制文件的命令行参数。
 *
 * @author android_zero
 */
data class ClangdSettings(
    val backgroundIndex: Boolean = true,
    val clangTidy: Boolean = true,
    val completionStyle: String = "detailed",
    val headerInsertion: String = "iwyu",
    val functionArgPlaceholders: Boolean = true
) {
    /**
     * 将当前配置转化为传递给 clangd 进程的命令行参数列表。
     *
     * @param compileCommandsDir compile_commands.json 所在的目录
     * @return 参数字符串列表
     */
    fun buildCommandArgs(compileCommandsDir: File?): List<String> {
        val args = mutableListOf<String>()
        
        args.add("--background-index=${if (backgroundIndex) "true" else "false"}")
        args.add("--clang-tidy=${if (clangTidy) "true" else "false"}")
        args.add("--completion-style=$completionStyle")
        args.add("--header-insertion=$headerInsertion")
        args.add("--function-arg-placeholders=${if (functionArgPlaceholders) "true" else "false"}")
        args.add("--pch-storage=memory") // 移动端建议放入内存，减少磁盘 I/O
        
        if (compileCommandsDir != null && compileCommandsDir.exists()) {
            args.add("--compile-commands-dir=${compileCommandsDir.absolutePath}")
        }
        
        return args
    }
}

/**
 * 依托于 AndroidIDE [PrefBasedServerSettings] 的 Clangd 服务器特定设置。
 *
 * 负责从 Shared Preferences 中读取用户设置的 NDK 版本以及开关配置。
 *
 * @author android_zero
 */
class ClangdServerSettings : PrefBasedServerSettings() {

    companion object {
        const val KEY_TARGET_NDK_VERSION = "lsp_clangd_target_ndk_version"
    }

    /**
     * 获取用户通过 UI 设置的目标 NDK 版本。
     */
    val targetNdkVersion: String?
        get() = prefs?.getString(KEY_TARGET_NDK_VERSION, null)

    /**
     * 获取 Clangd 内部运行参数配置。
     */
    val clangdSettings: ClangdSettings
        get() = ClangdSettings() // 默认返回基础配置，后期可扩展从 prefs 读取

    override fun codeAnalysisEnabled(): Boolean {
        return super.codeAnalysisEnabled()
    }

    override fun completionsEnabled(): Boolean {
        return super.completionsEnabled()
    }
}