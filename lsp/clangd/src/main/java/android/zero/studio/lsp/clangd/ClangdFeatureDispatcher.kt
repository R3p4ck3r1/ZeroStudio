package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Range
import java.nio.file.Path

/** Small typed facade used by editor glue code to dispatch optional clangd features safely. */
class ClangdFeatureDispatcher(private val server: ClangdLanguageServer) {
  suspend fun hover(params: DefinitionParams): MarkupContent = server.hover(params)
  suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp = server.signatureHelp(params)
  suspend fun references(params: ReferenceParams): ReferenceResult = server.findReferences(params)
  suspend fun definition(params: DefinitionParams): DefinitionResult = server.findDefinition(params)
  suspend fun symbols(file: Path): DocumentSymbolsResult = server.documentSymbols(file)
  suspend fun semanticTokens(params: SemanticTokensParams): SemanticTokens = server.semanticTokensFull(params)
  suspend fun inlayHints(params: InlayHintParams): List<InlayHint> = server.inlayHints(params)
  suspend fun rename(params: RenameParams): WorkspaceEdit = server.rename(params)
  suspend fun prepareRename(params: DefinitionParams): PrepareRenameResult? = server.prepareRename(params)
  suspend fun foldingRanges(file: Path): List<FoldingRange> = server.foldingRanges(file)
  suspend fun selectionRanges(params: SelectionRangesParams): List<SelectionRange> = server.selectionRanges(params)
  suspend fun documentLinks(file: Path): List<DocumentLink> = server.documentLinks(file)
  suspend fun codeLens(file: Path): List<CodeLens> = server.codeLens(file)
  suspend fun expandSelection(params: ExpandSelectionParams): Range = server.expandSelection(params)
}
