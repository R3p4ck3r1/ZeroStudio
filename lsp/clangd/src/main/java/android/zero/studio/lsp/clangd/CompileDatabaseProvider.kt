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
 * 编译数据库协调提供者。
 *
 * 功能与用途：
 * 负责在 Clangd 启动前，确保项目拥有可用的 `compile_commands.json`。
 * 策略如下：
 * 1. 扫描常见的 CMake 构建目录（如 AGP 生成的 `.cxx/cmake/...`）。
 * 2. 如果找到 AGP 自动生成的文件，直接将该目录提供给 Clangd 使用。
 * 3. 如果找不到，则扫描整个项目的 C/C++ 源文件，并调用 [CompileCommandsGenerator] 手动生成兜底数据库。
 *
 * @author android_zero
 */
object CompileDatabaseProvider {

    private val log = LoggerFactory.getLogger(CompileDatabaseProvider::class.java)

    /** 常见的 CMake 生成目录 */
    private val SEARCH_PATHS = listOf(
        "app/.cxx",
        ".cxx",
        "app/build/intermediates/cmake",
        "build/intermediates/cmake",
        "build/compile_commands.json"
    )

    /** C/C++ 源码扩展名 */
    private val SOURCE_EXTENSIONS = setOf("c", "cpp", "cxx", "cc", "m", "mm")

    /**
     * 提供或生成编译命令数据库所在的目录。
     *
     * @param projectDir 项目根目录
     * @param toolchainInfo 解析好的工具链信息
     * @return 包含 compile_commands.json 的目录。必定不为 null。
     */
    fun ensureCompileCommandsDir(projectDir: File, toolchainInfo: ClangdSysrootResolver.ToolchainInfo): File {
        // 1. 尝试寻找 AGP 自动生成的文件
        for (path in SEARCH_PATHS) {
            val targetDir = File(projectDir, path)
            if (targetDir.exists() && targetDir.isDirectory) {
                val dbFile = targetDir.walkTopDown().find { it.name == "compile_commands.json" }
                if (dbFile != null) {
                    log.info("Found existing compile_commands.json from AGP at: {}", dbFile.absolutePath)
                    return dbFile.parentFile
                }
            } else if (targetDir.isFile && targetDir.name == "compile_commands.json") {
                log.info("Found existing compile_commands.json at: {}", targetDir.absolutePath)
                return targetDir.parentFile
            }
        }

        // 2. 兜底方案：自己生成
        log.warn("No existing compile_commands.json found. Generating fallback database...")
        
        val sourceFiles = mutableListOf<File>()
        projectDir.walkTopDown().forEach { file ->
            if (file.isFile && SOURCE_EXTENSIONS.contains(file.extension.lowercase())) {
                // 排除 build 目录等生成目录
                if (!file.absolutePath.contains("/build/") && !file.absolutePath.contains("/.gradle/")) {
                    sourceFiles.add(file)
                }
            }
        }

        if (sourceFiles.isEmpty()) {
            log.info("No C/C++ source files found in the project. Returning project root.")
            return projectDir
        }

        // 提取项目中可能存在的 include 目录 (比如 include/ 或者 src/main/cpp/include/)
        val includeDirs = mutableListOf<File>()
        projectDir.walkTopDown().forEach { file ->
            if (file.isDirectory && (file.name == "include" || file.name == "headers")) {
                includeDirs.add(file)
            }
        }

        // 生成
        val dbFile = CompileCommandsGenerator.generate(
            projectPath = projectDir.absolutePath,
            sourceFiles = sourceFiles,
            toolchainInfo = toolchainInfo,
            includeDirs = includeDirs
        )

        return dbFile.parentFile
    }
}