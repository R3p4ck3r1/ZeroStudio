package android.zero.studio.lsp.clangd

import java.io.File

object CompileDatabaseProvider {
  fun ensureCompileCommandsDir(projectDir: File, toolchain: NdkToolchainLocator.Toolchain): File? {
    val existing = CompileCommandsLocator.find(projectDir)
    if (existing != null) return existing.parentFile
    val generated = CompileCommandsGenerator.generate(projectDir, toolchain)
    return generated.parentFile
  }
}
