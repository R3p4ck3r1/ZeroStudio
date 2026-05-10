package android.zero.studio.lsp.clangd

import java.nio.file.Path

object ClangdEditorIntegration {
  fun supports(file: Path): Boolean = file.fileName.toString().substringAfterLast('.', "").lowercase() in ClangdCompletionProvider.EXTENSIONS
}
