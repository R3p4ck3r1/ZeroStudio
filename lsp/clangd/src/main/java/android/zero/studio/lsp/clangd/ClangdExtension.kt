package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.api.ILanguageServerRegistry

object ClangdExtension {
  fun register(registry: ILanguageServerRegistry = ILanguageServerRegistry.getDefault()) {
    registry.register(ClangdLanguageServer())
  }
}
