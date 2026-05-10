package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.api.AbstractServiceProvider
import com.itsaky.androidide.lsp.api.ICompletionProvider
import com.itsaky.androidide.lsp.api.ILanguageServerRegistry
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import java.nio.file.Path

class ClangdCompletionProvider : AbstractServiceProvider(), ICompletionProvider {
  override fun canComplete(file: Path): Boolean = file.extension.lowercase() in EXTENSIONS
  override fun complete(params: CompletionParams): CompletionResult {
    abortCompletionIfCancelled()
    val server = ILanguageServerRegistry.getDefault().getServer(ClangdLanguageServer.SERVER_ID) as? ClangdLanguageServer
    return server?.complete(params) ?: CompletionResult.EMPTY
  }
  private val Path.extension: String get() = fileName.toString().substringAfterLast('.', "")
  companion object { val EXTENSIONS = setOf("c", "cc", "cpp", "cxx", "h", "hh", "hpp", "hxx") }
}
