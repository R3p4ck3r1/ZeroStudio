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

import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.utils.Environment
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory

/**
 * 编译命令数据库 (compile_commands.json) 生成引擎。
 *
 * 功能与用途：
 * 动态为项目中的 C/C++ 源文件生成 `compile_commands.json`。
 * Clangd 依靠此文件了解每个源文件的编译参数（如 `-I` 头文件路径，`-D` 宏定义，`--target` 目标平台），
 * 若无此文件，Clangd 将无法解析如 `<jni.h>` 或 `<string>` 等标准库，导致全屏报错。
 *
 * 工作流程线路图：
 * [CompileDatabaseProvider] 发起生成请求
 *   -> 获取 NDK Sysroot 和 Clang 编译器路径
 *   -> 遍历项目的 C/C++ 源文件
 *   -> 针对每个文件构造 `clang++ -target aarch64-linux-androidXX --sysroot=... -I... -c file.cpp` 形式的参数数组
 *   -> 序列化为 JSON 数组并写入磁盘
 *
 * 上下文与父类关系：
 * 作为一个独立的工具对象 (Object) 运行。被 [CompileDatabaseProvider] 调度。
 *
 * @author android_zero
 */
object CompileCommandsGenerator {

    private val log = LoggerFactory.getLogger(CompileCommandsGenerator::class.java)

    /** 默认的 Android 目标平台三元组 */
    private const val DEFAULT_TARGET_TRIPLE = "aarch64-linux-android24"

    /**
     * 生成编译命令数据库文件。
     *
     * @param projectPath 项目根目录的绝对路径
     * @param sourceFiles 需要被编译的源代码文件列表
     * @param toolchainInfo 解析完成的工具链信息（包含 Sysroot 和 Bin 目录）
     * @param includeDirs 额外的头文件包含路径
     * @param defines 额外的宏定义
     * @param targetNdkVersion 目标 NDK 版本号（用于附加的路径推导）
     * @return 生成的 compile_commands.json 文件对象
     */
    fun generate(
        projectPath: String,
        sourceFiles: List<File>,
        toolchainInfo: ClangdSysrootResolver.ToolchainInfo,
        includeDirs: List<File> = emptyList(),
        defines: List<String> = emptyList(),
        targetNdkVersion: String? = null
    ): File {
        val compileCommandsFile = File(projectPath, "compile_commands.json")
        val jsonArray = JSONArray()

        val clangBin = File(toolchainInfo.binDir, "clang").absolutePath
        val clangppBin = File(toolchainInfo.binDir, "clang++").absolutePath
        val sysrootPath = toolchainInfo.sysrootDir.absolutePath

        // 提取 target API level，AndroidIDE 项目默认可以使用 24
        val targetTriple = DEFAULT_TARGET_TRIPLE

        log.info("Generating compile_commands.json for {} source files. Sysroot: {}", sourceFiles.size, sysrootPath)

        sourceFiles.forEach { srcFile ->
            val ext = srcFile.extension.lowercase()
            val isCxx = ext == "cpp" || ext == "cc" || ext == "cxx" || ext == "mm"
            val compiler = if (isCxx) clangppBin else clangBin

            val args = mutableListOf<String>().apply {
                add(compiler)
                add("-target")
                add(targetTriple)
                
                // 挂载 NDK Sysroot
                add("--sysroot=$sysrootPath")
                
                // 语言标准
                if (isCxx) {
                    add("-std=c++17")
                    // Android NDK 的 C++ 标准库头文件通常位于 sysroot/usr/include/c++/v1
                    add("-isystem")
                    add("$sysrootPath/usr/include/c++/v1")
                } else {
                    add("-std=c11")
                }
                
                // C 标准库系统头文件
                add("-isystem")
                add("$sysrootPath/usr/include")
                add("-isystem")
                add("$sysrootPath/usr/include/aarch64-linux-android") // 默认补全 aarch64 架构相关头文件

                // Android 特有宏
                add("-DANDROID")
                add("-D__ANDROID__")
                add("-D__ANDROID_API__=24") // 假设 API 24
                
                // 自定义宏定义
                defines.forEach { add("-D$it") }
                
                // 自定义包含目录
                includeDirs.forEach { 
                    if (it.exists()) add("-I${it.absolutePath}") 
                }
                
                // 当前项目根目录也作为包含目录
                add("-I$projectPath")

                add("-c")
                add(srcFile.absolutePath)
                
                // 输出占位符（clangd 只需要参数，不需要真正的输出文件，这里随便写一个以符合规范）
                add("-o")
                add(srcFile.absolutePath.replace(Regex("\\.[^.]+$"), ".o"))
            }

            val commandObj = JSONObject().apply {
                put("directory", projectPath)
                put("file", srcFile.absolutePath)
                put("arguments", JSONArray(args))
            }
            jsonArray.put(commandObj)
        }

        try {
            compileCommandsFile.writeText(jsonArray.toString(2))
            log.info("Successfully wrote compile_commands.json to {}", compileCommandsFile.absolutePath)
        } catch (e: Exception) {
            log.error("Failed to write compile_commands.json", e)
        }

        return compileCommandsFile
    }
}