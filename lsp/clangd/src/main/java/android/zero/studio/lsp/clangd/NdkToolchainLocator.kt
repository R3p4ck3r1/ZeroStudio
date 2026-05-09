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
 * AndroidIDE NDK 工具链探测器。
 *
 * 用途与功能：
 * 负责在 AndroidIDE 的 SDK 目录中寻找合适的 NDK 版本，并根据当前设备的 CPU ABI 
 * 智能解析出 llvm 工具链中 `clangd` 二进制文件的绝对路径。
 *
 * 具备降级机制：
 * 1. 优先使用用户配置的特定 NDK 版本。
 * 2. 匹配当前设备的首选 CPU ABI（如 `linux-aarch64`）。
 * 3. 若当前 ABI 缺失，自动降级探测该 NDK 下其他可用的 Linux ABI 目录。
 * 4. 彻底缺失时抛出明确异常。
 *
 * @author android_zero
 */
object NdkToolchainLocator {

    private val log = LoggerFactory.getLogger(NdkToolchainLocator::class.java)

    private const val LLVM_TOOLCHAIN_REL_PATH = "toolchains/llvm/prebuilt"
    private const val CLANGD_BIN_NAME = "clangd"

    /**
     * 获取系统中已安装的所有 NDK 版本列表。
     *
     * @return 包含所有可用 NDK 版本号（文件夹名称）的列表，若无则返回空列表。
     */
    @JvmStatic
    fun getAvailableNdkVersions(): List<String> {
        val ndkDir = File(Environment.ANDROID_HOME, "ndk")
        if (!ndkDir.exists() || !ndkDir.isDirectory) {
            return emptyList()
        }
        return ndkDir.listFiles { file -> file.isDirectory }
            ?.map { it.name }
            ?.sortedDescending() ?: emptyList()
    }

    /**
     * 探测并获取指定 NDK 版本下的 `clangd` 二进制文件。
     *
     * @param targetNdkVersion 目标 NDK 版本（如 "27.1.12297006"）。若为空，则自动选择最新版本。
     * @return 指向 `clangd` 的 File 对象。
     * @throws IllegalStateException 当无法找到有效的 NDK 或 clangd 文件时抛出。
     */
    @JvmStatic
    fun resolveClangdExecutable(targetNdkVersion: String?): File {
        val ndkDir = File(Environment.ANDROID_HOME, "ndk")
        if (!ndkDir.exists() || !ndkDir.isDirectory) {
            throw IllegalStateException("Android NDK directory not found at: ${ndkDir.absolutePath}")
        }

        // 决定使用的 NDK 版本
        val versionToUse = targetNdkVersion?.takeIf { it.isNotBlank() && File(ndkDir, it).exists() }
            ?: getAvailableNdkVersions().firstOrNull()
            ?: throw IllegalStateException("No valid NDK versions found in ${ndkDir.absolutePath}")

        val selectedNdkRoot = File(ndkDir, versionToUse)
        val prebuiltDir = File(selectedNdkRoot, LLVM_TOOLCHAIN_REL_PATH)

        if (!prebuiltDir.exists() || !prebuiltDir.isDirectory) {
            throw IllegalStateException("LLVM toolchain directory missing: ${prebuiltDir.absolutePath}")
        }

        // 将 Android ABI 映射为 Linux NDK 工具链目录格式
        val targetLinuxAbiDirName = mapAndroidAbiToLinuxAbiDir(Build.SUPPORTED_ABIS)
        var targetPrebuiltDir = File(prebuiltDir, targetLinuxAbiDirName)

        // 降级方案：如果首选 ABI 目录不存在，探测其他存在的 linux-{abi} 目录
        if (!targetPrebuiltDir.exists() || !targetPrebuiltDir.isDirectory) {
            log.warn("Preferred toolchain dir {} not found, attempting fallback...", targetPrebuiltDir.absolutePath)
            val fallbackDir = prebuiltDir.listFiles { file -> 
                file.isDirectory && file.name.startsWith("linux-") 
            }?.firstOrNull()

            if (fallbackDir != null) {
                log.info("Fallback toolchain dir found: {}", fallbackDir.absolutePath)
                targetPrebuiltDir = fallbackDir
            } else {
                throw IllegalStateException("No valid linux-{cpu_abi} toolchain found in ${prebuiltDir.absolutePath}")
            }
        }

        val binDir = File(targetPrebuiltDir, "bin")
        val clangdBin = File(binDir, CLANGD_BIN_NAME)

        if (!clangdBin.exists() || !clangdBin.isFile) {
            throw IllegalStateException("clangd executable not found at: ${clangdBin.absolutePath}")
        }

        // 确保文件具有可执行权限
        if (!clangdBin.canExecute()) {
            clangdBin.setExecutable(true)
        }

        log.info("Successfully resolved clangd executable: {}", clangdBin.absolutePath)
        return clangdBin
    }

    /**
     * 将设备的 Android ABI 映射为 NDK 预编译链的文件夹名称。
     *
     * @param supportedAbis 设备的 Build.SUPPORTED_ABIS 数组
     * @return 对应的文件夹名称（例如 "linux-aarch64"）
     */
    private fun mapAndroidAbiToLinuxAbiDir(supportedAbis: Array<String>): String {
        for (abi in supportedAbis) {
            when (abi) {
                "arm64-v8a" -> return "linux-aarch64"
                "x86_64" -> return "linux-x86_64"
                "armeabi-v7a", "armeabi" -> return "linux-arm"
                "x86" -> return "linux-x86"
            }
        }
        // 默认降级为 aarch64
        return "linux-x86_64"
    }
}