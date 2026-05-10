package android.zero.studio.lsp.clangd

import java.io.File

object CompileCommandsLocator {
  fun find(projectDir: File): File? {
    if (!projectDir.isDirectory) return null
    return projectDir.walkTopDown()
        .onEnter { it.name != ".git" && it.name != ".gradle" && it.name != ".cxx" }
        .firstOrNull { it.isFile && it.name == "compile_commands.json" }
  }
}
