package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.util.PrefBasedServerSettings
import java.io.File

data class ClangdSettings(
    val backgroundIndex: Boolean = true,
    val clangTidy: Boolean = true,
    val completionStyle: String = "detailed",
    val headerInsertion: String = "iwyu",
    val functionArgPlaceholders: Boolean = true,
    val pchStorage: String = "memory",
) {
  fun buildCommandArgs(compileCommandsDir: File?, toolchain: NdkToolchainLocator.Toolchain? = null): List<String> {
    val args = mutableListOf<String>()
    args += "--background-index=${if (backgroundIndex) "true" else "false"}"
    args += "--clang-tidy=${if (clangTidy) "true" else "false"}"
    args += "--completion-style=$completionStyle"
    args += "--header-insertion=$headerInsertion"
    args += "--function-arg-placeholders=${if (functionArgPlaceholders) "true" else "false"}"
    args += "--pch-storage=$pchStorage"
    args += "--log=error"
    args += "--offset-encoding=utf-16"
    compileCommandsDir?.takeIf { it.isDirectory }?.let { args += "--compile-commands-dir=${it.absolutePath}" }
    toolchain?.binDir?.takeIf { it.isDirectory }?.let { args += "--query-driver=${File(it, "*-clang*").absolutePath}" }
    return args
  }
}

class ClangdServerSettings : PrefBasedServerSettings() {
  companion object {
    const val KEY_TARGET_NDK_VERSION = "lsp_clangd_target_ndk_version"
    const val KEY_BACKGROUND_INDEX = "lsp_clangd_background_index"
    const val KEY_CLANG_TIDY = "lsp_clangd_clang_tidy"
  }

  val targetNdkVersion: String?
    get() = prefs?.getString(KEY_TARGET_NDK_VERSION, null)?.takeIf { it.isNotBlank() }

  val clangdSettings: ClangdSettings
    get() = ClangdSettings(
        backgroundIndex = prefs?.getBoolean(KEY_BACKGROUND_INDEX, true) ?: true,
        clangTidy = prefs?.getBoolean(KEY_CLANG_TIDY, true) ?: true,
    )
}
