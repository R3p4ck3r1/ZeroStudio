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
        val parameters = signature.optJSONArray("parameters") ?: JSONArray()
        SignatureInformation(
            signature.optString("label", ""),
            ClangdProtocol.toMarkup(signature.opt("documentation")),
            (0 until parameters.length()).mapNotNull { p -> parameters.optJSONObject(p) }.map { ParameterInformation(it.optString("label", ""), ClangdProtocol.toMarkup(it.opt("documentation"))) },
        )
      }
    }
    return SignatureHelp(mapped, result.optInt("activeSignature", 0), result.optInt("activeParameter", 0))
  }

  fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens {
    val result = request("textDocument/semanticTokens/full", JSONObject().put("textDocument", ClangdProtocol.textDocument(params.file))).optJSONObject("result") ?: return SemanticTokens()
    return SemanticTokens(toIntList(result.optJSONArray("data")), if (result.has("resultId")) result.optString("resultId") else null)
  }

  fun semanticTokensRange(params: SemanticTokensParams): SemanticTokens {
    val requestedRange = params.range ?: Range.NONE
    val result = request("textDocument/semanticTokens/range", JSONObject().put("textDocument", ClangdProtocol.textDocument(params.file)).put("range", ClangdProtocol.range(requestedRange))).optJSONObject("result") ?: return SemanticTokens()
    return SemanticTokens(toIntList(result.optJSONArray("data")), if (result.has("resultId")) result.optString("resultId") else null)
  }

  fun inlayHints(params: InlayHintParams): List<InlayHint> {
    val result = request("textDocument/inlayHint", JSONObject().put("textDocument", ClangdProtocol.textDocument(params.file)).put("range", ClangdProtocol.range(params.range))).optJSONArray("result") ?: return emptyList()
    return (0 until result.length()).mapNotNull { result.optJSONObject(it) }.map { obj ->
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
    val symbols = (0 until result.length()).mapNotNull { result.optJSONObject(it) }.map { symbol(it) }
    return DocumentSymbolsResult(symbols = symbols)
  }

  fun format(file: Path): List<TextEdit> {
    val result = request("textDocument/formatting", JSONObject().put("textDocument", ClangdProtocol.textDocument(file)).put("options", JSONObject().put("tabSize", 4).put("insertSpaces", true))).optJSONArray("result") ?: return emptyList()
    return (0 until result.length()).mapNotNull { ClangdProtocol.textEdit(result.optJSONObject(it)) }
  }

  private fun symbol(obj: JSONObject): DocumentSymbol = DocumentSymbol(
      name = obj.optString("name", ""),
      detail = obj.optString("detail", ""),
      kind = ClangdProtocol.symbolKind(obj.optInt("kind", 0)),
      range = ClangdProtocol.toRange(obj.optJSONObject("range")),
      selectionRange = ClangdProtocol.toRange(obj.optJSONObject("selectionRange")),
      children = (obj.optJSONArray("children") ?: JSONArray()).let { children -> (0 until children.length()).mapNotNull { children.optJSONObject(it) }.map(::symbol) },
  )

  private fun toIntList(array: JSONArray?): List<Int> = if (array == null) emptyList() else (0 until array.length()).map { array.optInt(it) }
}
