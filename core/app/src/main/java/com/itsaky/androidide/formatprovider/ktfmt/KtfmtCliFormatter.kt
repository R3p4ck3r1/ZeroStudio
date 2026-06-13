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
package com.itsaky.androidide.formatprovider.ktfmt

import android.content.Context
import com.itsaky.androidide.formatprovider.CodeFormatter
import com.itsaky.androidide.preferences.KtfmtPreferences
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.executioncommand.TermuxCommand
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * CLI 版本的 Ktfmt 格式化提供器
 *
 * 通过 TermuxCommand 进行格式化交互。支持 Stdin 管道传输和 File 路径传递两种模式。
 *
 * @author android_zero
 */
class KtfmtCliFormatter(private val context: Context, private val file: File) : CodeFormatter {

  override fun format(source: String): String {
    if (!KtfmtEnv.isInstalled()) return source

    // 获取项目根目录 (用于 .editorconfig 读取)
    // 治本：projectDir 改 nullable 后，未打开工程时回退到 file.parentFile（保留旧语义）
    val projectRoot =
        try {
          IProjectManager.getInstance().projectDir ?: file.parentFile
        } catch (e: Exception) {
          file.parentFile
        }

    val args = mutableListOf("-jar", KtfmtEnv.KTFMT_JAR.absolutePath)

    // 追加风格选项
    args.add(KtfmtPreferences.style)

    if (KtfmtPreferences.keepImports) {
      args.add("--do-not-remove-unused-imports")
    }
    if (KtfmtPreferences.enableEditorConfig) {
      args.add("--enable-editorconfig")
    }
    if (KtfmtPreferences.quietMode) {
      args.add("--quiet")
    }

    val mode = KtfmtPreferences.formatMode

    if (mode == "stdin") {
      args.add("--stdin-name=${file.absolutePath}")
      args.add("-")

      // 治本：projectRoot 在上面已经能 fallback 到 file.parentFile，但为了编译期
      // 避免 smart cast 失效，构造一个本地 val 让下面的 ?.. 表达式更清晰
      val root = projectRoot ?: file.parentFile
      val result = runBlocking {
        TermuxCommand.run(context) {
          label("Ktfmt CLI (Stdin)")
          executable(Environment.JAVA.absolutePath)
          args(*args.toTypedArray())
          workingDir(root.absolutePath)
          stdin(source)
        }
      }

      if (result.isSuccess) {
        return result.stdout
      } else {
        throw RuntimeException(
            "Ktfmt formatting failed. Code: ${result.exitCode}\nError: ${result.stderr}"
        )
      }
    } else {
      // File 模式 (默认 & 更稳定)
      file.writeText(source)

      args.add(file.absolutePath)

      val root = projectRoot ?: file.parentFile
      val result = runBlocking {
        TermuxCommand.run(context) {
          label("Ktfmt CLI (File)")
          executable(Environment.JAVA.absolutePath)
          args(*args.toTypedArray())
          workingDir(root.absolutePath)
        }
      }

      if (result.isSuccess) {
        // 读取已在硬盘中被格式化好的文件内容并返回
        return file.readText()
      } else {
        throw RuntimeException(
            "Ktfmt formatting failed. Code: ${result.exitCode}\nError: ${result.stderr}"
        )
      }
    }
  }
}
