package android.zero.studio.lsp.clangd

import java.io.File

object ClangdSysrootResolver {
  fun resolve(projectDir: File, settings: ClangdServerSettings = ClangdServerSettings()): NdkToolchainLocator.Toolchain =
      NdkToolchainLocator.resolve(projectDir, settings)
}
