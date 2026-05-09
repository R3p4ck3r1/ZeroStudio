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
 * Gradle 脚本 NDK 版本解析器。
 *
 * 用途与功能：
 * 用于扫描和解析 Android 项目的 `build.gradle` (Groovy DSL) 或 `build.gradle.kts` (Kotlin DSL) 文件，
 * 精准提取出用户配置的 `ndkVersion` 属性值。
 * 
 * 工作流程：
 * 1. 接收项目根目录或 app 模块目录。
 * 2. 依次寻找 `build.gradle.kts` 或 `build.gradle`。
 * 3. 使用正则表达式匹配 `ndkVersion = "xxx"` 或 `ndkVersion "xxx"` 格式。
 * 4. 返回提取到的版本号文本。
 *
 * @author android_zero
 */
object GradleNdkVersionParser {

    private val log = LoggerFactory.getLogger(GradleNdkVersionParser::class.java)

    // 匹配 Groovy: ndkVersion "27.1.12297006" 或 Kotlin: ndkVersion = "27.1.12297006"
    // 兼容单引号和双引号
    private val NDK_VERSION_REGEX = Regex("""ndkVersion\s*=?\s*['"]([^'"]+)['"]""")

    /**
     * 探测指定目录下的构建脚本，并提取 NDK 版本号。
     *
     * @param moduleDir 包含 build.gradle(.kts) 的目录（通常是 app 模块）
     * @return 提取到的 NDK 版本号，若未配置或解析失败则返回 null
     */
    @JvmStatic
    fun parseNdkVersion(moduleDir: File): String? {
        val ktsFile = File(moduleDir, "build.gradle.kts")
        val groovyFile = File(moduleDir, "build.gradle")

        val targetFile = when {
            ktsFile.exists() && ktsFile.isFile -> ktsFile
            groovyFile.exists() && groovyFile.isFile -> groovyFile
            else -> {
                log.debug("No build.gradle(.kts) found in {}", moduleDir.absolutePath)
                return null
            }
        }

        try {
            val content = targetFile.readText()
            val matchResult = NDK_VERSION_REGEX.find(content)
            
            if (matchResult != null) {
                val version = matchResult.groupValues[1]
                log.info("Extracted ndkVersion '{}' from {}", version, targetFile.name)
                return version
            }
        } catch (e: Exception) {
            log.error("Failed to parse ndkVersion from ${targetFile.name}", e)
        }

        return null
    }
}