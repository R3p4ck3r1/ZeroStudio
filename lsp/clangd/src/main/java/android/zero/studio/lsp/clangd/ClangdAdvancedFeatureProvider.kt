package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Range
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

class ClangdAdvancedFeatureProvider(private val rpc: JsonRpcMessenger) {
  private val timeoutSeconds = 5L

  fun request(method: String, params: JSONObject): JSONObject = rpc.sendRequest(method, params).get(timeoutSeconds, TimeUnit.SECONDS)

  fun hover(params: DefinitionParams): MarkupContent {
    val result = request("textDocument/hover", ClangdProtocol.textPosition(params.file, params.position)).opt("result")
    return if (result is JSONObject) ClangdProtocol.toMarkup(result.opt("contents")) else MarkupContent()
  }

  fun signatureHelp(params: SignatureHelpParams): SignatureHelp {
    val result = request("textDocument/signatureHelp", ClangdProtocol.textPosition(params.file, params.position)).optJSONObject("result") ?: return SignatureHelp(emptyList(), 0, 0)
    val signatures = result.optJSONArray("signatures") ?: JSONArray()
    val mapped = (0 until signatures.length()).mapNotNull { i ->
      signatures.optJSONObject(i)?.let { signature ->
        val label = signature.optString("label", "")
        val parameters = signature.optJSONArray("parameters") ?: JSONArray()
        SignatureInformation(
            label,
            ClangdProtocol.toMarkup(signature.opt("documentation")),
            (0 until parameters.length()).mapNotNull { p -> parameters.optJSONObject(p) }.map { parameter ->
              ParameterInformation(parameterLabel(parameter.opt("label"), label), ClangdProtocol.toMarkup(parameter.opt("documentation")))
            },
        )
      }
    }
    return SignatureHelp(mapped, result.optInt("activeSignature", 0), result.optInt("activeParameter", 0))
  }

  fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens {
    val result = request("textDocument/semanticTokens/full", JSONObject().put("textDocument", ClangdProtocol.textDocument(params.file))).optJSONObject("result") ?: return SemanticTokens()
    return SemanticTokens(toIntList(result.optJSONArray("data")), result.optStringOrNull("resultId"))
  }

  fun semanticTokensRange(params: SemanticTokensParams): SemanticTokens {
    val requestedRange = params.range ?: Range.NONE
    val result = request("textDocument/semanticTokens/range", JSONObject().put("textDocument", ClangdProtocol.textDocument(params.file)).put("range", ClangdProtocol.range(requestedRange))).optJSONObject("result") ?: return SemanticTokens()
    return SemanticTokens(toIntList(result.optJSONArray("data")), result.optStringOrNull("resultId"))
  }

  fun inlayHints(params: InlayHintParams): List<InlayHint> {
    val result = request("textDocument/inlayHint", JSONObject().put("textDocument", ClangdProtocol.textDocument(params.file)).put("range", ClangdProtocol.range(params.range))).optJSONArray("result") ?: return emptyList()
    return result.objects().map { obj ->
      val label = when (val raw = obj.opt("label")) {
        is String -> raw
        is JSONArray -> (0 until raw.length()).joinToString("") { i -> raw.optJSONObject(i)?.optString("value") ?: raw.optString(i) }
        else -> ""
      }
      InlayHint(ClangdProtocol.toPosition(obj.optJSONObject("position")), label, if (obj.optInt("kind", 1) == 2) InlayHintKind.Parameter else InlayHintKind.Type)
    }
  }

  fun documentSymbols(file: Path): DocumentSymbolsResult {
    val result = request("textDocument/documentSymbol", JSONObject().put("textDocument", ClangdProtocol.textDocument(file))).optJSONArray("result") ?: return DocumentSymbolsResult()
    val objects = result.objects()
    val hierarchical = objects.filter { it.has("selectionRange") }.map { symbol(it) }
    val flat = objects.filter { it.has("location") }.map { info(it) }
    return DocumentSymbolsResult(symbols = hierarchical, flatSymbols = flat)
  }

  fun format(file: Path): List<TextEdit> {
    val result = request("textDocument/formatting", JSONObject().put("textDocument", ClangdProtocol.textDocument(file)).put("options", JSONObject().put("tabSize", 4).put("insertSpaces", true))).optJSONArray("result") ?: return emptyList()
    return result.objects().mapNotNull { ClangdProtocol.textEdit(it) }
  }

  fun rename(params: RenameParams): WorkspaceEdit = ClangdProtocol.workspaceEdit(
      request("textDocument/rename", ClangdProtocol.textPosition(params.file, params.position).put("newName", params.newName)).optJSONObject("result"),
  )

  fun prepareRename(params: DefinitionParams): PrepareRenameResult? {
    val result = request("textDocument/prepareRename", ClangdProtocol.textPosition(params.file, params.position)).opt("result") ?: return null
    return when (result) {
      is JSONObject -> PrepareRenameResult(ClangdProtocol.toRange(result.optJSONObject("range") ?: result), result.optString("placeholder", ""), true)
      else -> null
    }
  }

  fun codeActions(file: Path, range: Range, diagnostics: List<DiagnosticItem>): List<CodeActionItem> {
    val context = JSONObject().put("diagnostics", JSONArray().also { diagnostics.forEach { item -> it.put(ClangdProtocol.diagnostic(item)) } })
    val result = request("textDocument/codeAction", JSONObject().put("textDocument", ClangdProtocol.textDocument(file)).put("range", ClangdProtocol.range(range)).put("context", context)).optJSONArray("result") ?: return emptyList()
    return result.objects().map { ClangdProtocol.codeAction(it) }
  }

  fun foldingRanges(file: Path): List<FoldingRange> {
    val result = request("textDocument/foldingRange", JSONObject().put("textDocument", ClangdProtocol.textDocument(file))).optJSONArray("result") ?: return emptyList()
    return result.objects().map { obj ->
      FoldingRange(
          startLine = obj.optInt("startLine", 0),
          startCharacter = obj.optInt("startCharacter", 0),
          endLine = obj.optInt("endLine", 0),
          endCharacter = obj.optInt("endCharacter", 0),
          kind = when (obj.optString("kind", "region")) {
            "comment" -> FoldingRangeKind.Comment
            "imports" -> FoldingRangeKind.Imports
            else -> FoldingRangeKind.Region
          },
      )
    }
  }

  fun selectionRanges(params: SelectionRangesParams): List<SelectionRange> {
    val result = request("textDocument/selectionRange", JSONObject().put("textDocument", ClangdProtocol.textDocument(params.file)).put("positions", JSONArray().also { params.positions.forEach { pos -> it.put(ClangdProtocol.pos(pos)) } })).optJSONArray("result") ?: return emptyList()
    return result.objects().map(::selectionRange)
  }

  fun documentLinks(file: Path): List<DocumentLink> {
    val result = request("textDocument/documentLink", JSONObject().put("textDocument", ClangdProtocol.textDocument(file))).optJSONArray("result") ?: return emptyList()
    return result.objects().map { DocumentLink(ClangdProtocol.toRange(it.optJSONObject("range")), it.optStringOrNull("target"), it.optStringOrNull("tooltip")) }
  }

  fun codeLens(file: Path): List<CodeLens> {
    val result = request("textDocument/codeLens", JSONObject().put("textDocument", ClangdProtocol.textDocument(file))).optJSONArray("result") ?: return emptyList()
    return result.objects().map { CodeLens(ClangdProtocol.toRange(it.optJSONObject("range")), ClangdProtocol.command(it.optJSONObject("command")), it.opt("data")) }
  }

  fun executeCommand(params: ExecuteCommandParams): Any? = request("workspace/executeCommand", JSONObject().put("command", params.command).put("arguments", JSONArray(params.arguments.orEmpty()))).opt("result")

  fun resolveCodeAction(action: CodeActionItem): CodeActionItem {
    val data = action.data ?: return action
    val result = request("codeAction/resolve", JSONObject().put("title", action.title).put("data", data)).optJSONObject("result") ?: return action
    return ClangdProtocol.codeAction(result)
  }

  private fun symbol(obj: JSONObject): DocumentSymbol = DocumentSymbol(
      name = obj.optString("name", ""),
      detail = obj.optString("detail", ""),
      kind = ClangdProtocol.symbolKind(obj.optInt("kind", 0)),
      range = ClangdProtocol.toRange(obj.optJSONObject("range")),
      selectionRange = ClangdProtocol.toRange(obj.optJSONObject("selectionRange")),
      children = (obj.optJSONArray("children") ?: JSONArray()).objects().map(::symbol),
  )

  private fun info(obj: JSONObject): SymbolInformation {
    val location = obj.optJSONObject("location") ?: JSONObject()
    return SymbolInformation(
        name = obj.optString("name", ""),
        kind = ClangdProtocol.symbolKind(obj.optInt("kind", 0)),
        location = ClangdProtocol.toLocation(location),
        containerName = obj.optStringOrNull("containerName"),
    )
  }

  private fun selectionRange(obj: JSONObject): SelectionRange = SelectionRange(
      ClangdProtocol.toRange(obj.optJSONObject("range")),
      obj.optJSONObject("parent")?.let(::selectionRange),
  )

  private fun parameterLabel(raw: Any?, signatureLabel: String): String = when (raw) {
    is String -> raw
    is JSONArray -> if (raw.length() >= 2) {
      val start = raw.optInt(0).coerceIn(0, signatureLabel.length)
      val end = raw.optInt(1).coerceIn(start, signatureLabel.length)
      signatureLabel.substring(start, end)
    } else ""
    else -> ""
  }

  private fun toIntList(array: JSONArray?): List<Int> = if (array == null) emptyList() else (0 until array.length()).map { array.optInt(it) }
  private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
  private fun JSONObject.optStringOrNull(name: String): String? = if (has(name) && !isNull(name)) optString(name) else null
}
