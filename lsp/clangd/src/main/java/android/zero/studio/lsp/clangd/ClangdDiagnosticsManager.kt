package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.models.*
import java.nio.file.Path
import org.json.JSONObject

object ClangdDiagnosticsManager {
  fun publish(client: ILanguageClient?, params: JSONObject) {
    val uri = params.optString("uri", "")
    if (uri.isBlank()) return
    val file = runCatching { ClangdProtocol.path(uri) }.getOrNull() ?: return
    client?.publishDiagnostics(DiagnosticResult(file, parse(file, params)))
  }

  fun parse(file: Path, params: JSONObject): List<DiagnosticItem> {
    val array = params.optJSONArray("diagnostics") ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { obj ->
      DiagnosticItem(
          message = obj.optString("message", ""),
          code = obj.opt("code")?.toString().orEmpty(),
          range = ClangdProtocol.toRange(obj.optJSONObject("range")),
          source = obj.optString("source", "clangd"),
          severity = when (obj.optInt("severity", 3)) {
            1 -> DiagnosticSeverity.ERROR
            2 -> DiagnosticSeverity.WARNING
            4 -> DiagnosticSeverity.HINT
            else -> DiagnosticSeverity.INFO
          },
      )
    }
  }
}
