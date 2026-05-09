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

import android.os.Build
import com.itsaky.androidide.utils.Environment
import java.io.File
import org.slf4j.LoggerFactory

/**
 * 智能的 Clangd 及其环境路径解析引擎。
 *
 * 用途与功能：
 * 满足需求：取得 ndkVersion 文本来拼接内部数据目录路径，即
 * `ANDROID_HOME/ndk/{$ndkVersion}/toolchains/llvm/prebuilt/linux-{cpu abi}/bin`
 * 同时验证 `clangd` 工具是否存在，并在目标不存在时提供合理的降级回退机制。
 *
 * @author android_zero
 */
object ClangdSysrootResolver {

    private val log = LoggerFactory.getLogger(ClangdSysrootResolver::class.java)

    data class ToolchainInfo(
        val clangdExecutable: File,
        val sysrootDir: File,
        val binDir: File
    )

    /**
     * 根据项目目录的构建脚本和设备 ABI，解析出完整的 C/C++ 工具链信息。
     *
     * @param projectDir 当前工作项目目录
     * @return 包含 clangd、sysroot 和 bin 目录的上下文信息
     * @throws IllegalStateException 若无法找到任何可用的工具链
     */
    @JvmStatic
    fun resolve(projectDir: File): ToolchainInfo {
        // 1. 尝试从 build.gradle 中提取用户声明的 ndkVersion
        var targetVersion = GradleNdkVersionParser.parseNdkVersion(projectDir)
        
        // 如果根目录没有，尝试去 app 目录找
        if (targetVersion == null) {
            val appDir = File(projectDir, "app")
            if (appDir.exists()) {
                targetVersion = GradleNdkVersionParser.parseNdkVersion(appDir)
            }
        }

        // 也可以被用户的偏好设置强制覆盖
        val userPrefVersion = ClangdServerSettings().targetNdkVersion
        if (!userPrefVersion.isNullOrBlank() && userPrefVersion != "Auto-detect") {
            targetVersion = userPrefVersion
        }

        return resolveInternal(targetVersion)
    }

    private fun resolveInternal(requestedVersion: String?): ToolchainInfo {
        val ndkHome = File(Environment.ANDROID_HOME, "ndk")
        if (!ndkHome.exists()) {
            throw IllegalStateException("NDK Home does not exist: ${ndkHome.absolutePath}")
        }

        // 获取该目录下所有的版本文件夹
        val availableVersions = ndkHome.listFiles { f -> f.isDirectory }
            ?.map { it.name }?.sortedDescending() ?: emptyList()

        if (availableVersions.isEmpty()) {
            throw IllegalStateException("No NDK versions installed in ${ndkHome.absolutePath}")
        }

        // 确定最终使用的 NDK 版本夹
        val versionToUse = if (requestedVersion != null && availableVersions.contains(requestedVersion)) {
            requestedVersion
        } else {
            log.warn("Requested NDK version '{}' not found or null. Falling back to latest: {}", 
                requestedVersion, availableVersions.first())
            availableVersions.first()
        }

        val prebuiltDir = File(ndkHome, "$versionToUse/toolchains/llvm/prebuilt")
        if (!prebuiltDir.exists()) {
            throw IllegalStateException("LLVM prebuilt directory missing: ${prebuiltDir.absolutePath}")
        }

        // 探测设备 ABI 并映射为 linux-{cpu abi}
        val targetAbiDir = determineLinuxAbiDir(prebuiltDir)
        
        val binDir = File(targetAbiDir, "bin")
        val clangdExe = File(binDir, "clangd")
        val sysrootDir = File(targetAbiDir, "sysroot")

        if (!clangdExe.exists() || !clangdExe.isFile) {
            throw IllegalStateException("clangd not found in bin directory: ${binDir.absolutePath}")
        }

        // 确保权限
        clangdExe.setExecutable(true)

        log.info("Resolved Clangd toolchain -> Bin: {}, Sysroot: {}", binDir.absolutePath, sysrootDir.absolutePath)
        
        return ToolchainInfo(
            clangdExecutable = clangdExe,
            sysrootDir = sysrootDir,
            binDir = binDir
        )
    }

    private fun determineLinuxAbiDir(prebuiltDir: File): File {
        val supportedAbis = Build.SUPPORTED_ABIS
        var idealName = "linux-aarch64" // Default
        
        for (abi in supportedAbis) {
            when (abi) {
                "arm64-v8a" -> { idealName = "linux-aarch64"; break }
                "x86_64" -> { idealName = "linux-x86_64"; break }
                "armeabi-v7a", "armeabi" -> { idealName = "linux-arm"; break }
                "x86" -> { idealName = "linux-x86"; break }
            }
        }

        val idealDir = File(prebuiltDir, idealName)
        if (idealDir.exists() && idealDir.isDirectory) {
            return idealDir
        }

        // 降级：如果不存在首选架构（可能是 NDK 被精简过），随便找一个 `linux-` 开头的
        log.warn("Ideal ABI directory '{}' not found in {}, attempting fallback.", idealName, prebuiltDir.absolutePath)
        
        val fallback = prebuiltDir.listFiles { f -> f.isDirectory && f.name.startsWith("linux-") }?.firstOrNull()
            ?: throw IllegalStateException("No valid linux-{cpu_abi} directory found in ${prebuiltDir.absolutePath}")
            
        log.info("Fallback to ABI directory: {}", fallback.name)
        return fallback
    }
}