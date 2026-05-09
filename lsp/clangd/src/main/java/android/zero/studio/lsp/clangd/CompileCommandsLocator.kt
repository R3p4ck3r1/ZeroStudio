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

import java.io.File
import org.slf4j.LoggerFactory

/**
 * 编译数据库 (compile_commands.json) 智能定位器。
 *
 * 用途与功能：
 * Clangd 极其依赖 `compile_commands.json` 来解析 CMake 项目的系统依赖头文件和宏。
 * Android Gradle Plugin (AGP) 在开启 Native Build (CMake) 后，默认会在
 * `.cxx/cmake/` 或 `app/.cxx/` 目录下生成该文件。
 *
 * 工作流程：
 * 1. 优先扫描项目内部 AGP 生成的 `.cxx` 目录。
 * 2. 找到后将其返回并让 clangd 挂载。
 * 3. 没找到时才退化使用 `CompileCommandsGenerator` 扫描文件硬生成。
 *
 * @author android_zero
 */
object CompileCommandsLocator {

    private val log = LoggerFactory.getLogger(CompileCommandsLocator::class.java)

    /**
     * 智能定位或生成项目适用的编译数据库目录。
     *
     * @param projectDir 项目根目录
     * @return 包含 compile_commands.json 的目录。如果都失败则返回 projectDir。
     */
    @JvmStatic
    fun locateOrGenerate(projectDir: File): File {
        // 1. 探查常见的 AGP Native 构建目录
        val searchPaths = listOf(
            "app/.cxx",
            ".cxx",
            "app/build/intermediates/cmake",
            "build/intermediates/cmake"
        )

        for (path in searchPaths) {
            val targetDir = File(projectDir, path)
            if (targetDir.exists() && targetDir.isDirectory) {
                // 深度遍历找 json
                val dbFile = targetDir.walkTopDown().find { it.name == "compile_commands.json" }
                if (dbFile != null) {
                    log.info("Smart-located AGP generated compile_commands.json at: {}", dbFile.absolutePath)
                    return dbFile.parentFile
                }
            }
        }

        // 2. 如果没找到，退化到手动生成模式（针对无 CMake 配置的纯 C++ 算法测试项目）
        log.warn("No AGP compile_commands.json found, attempting to generate fallback DB...")
        val cxxFiles = projectDir.walkTopDown().filter { 
            val n = it.name.lowercase()
            n.endsWith(".c") || n.endsWith(".cpp") || n.endsWith(".cc") || n.endsWith(".cxx")
        }.toList()
        
        if (cxxFiles.isNotEmpty()) {
            try {
                val toolchain = ClangdSysrootResolver.resolve(projectDir)
                val generated = CompileCommandsGenerator.generateCompileCommands(
                    projectRoot = projectDir,
                    sourceFiles = cxxFiles,
                    includeDirs = listOf(File(toolchain.sysrootDir, "usr/include"))
                )
                if (generated.exists()) {
                    return generated.parentFile
                }
            } catch (e: Exception) {
                log.error("Fallback generation of compile_commands.json failed", e)
            }
        }

        return projectDir
    }
}