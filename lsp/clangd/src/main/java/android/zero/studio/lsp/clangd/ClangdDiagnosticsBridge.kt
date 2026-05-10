package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.models.DiagnosticItem
import com.itsaky.androidide.lsp.models.DiagnosticResult
import java.nio.file.Path
import org.json.JSONObject

/** Bridges clangd publishDiagnostics notifications into AndroidIDE diagnostics. */
object ClangdDiagnosticsBridge {
  fun publish(client: ILanguageClient?, params: JSONObject) = ClangdDiagnosticsManager.publish(client, params)
  fun parse(file: Path, params: JSONObject): List<DiagnosticItem> = ClangdDiagnosticsManager.parse(file, params)
  fun publish(client: ILanguageClient?, file: Path, diagnostics: List<DiagnosticItem>) {
    client?.publishDiagnostics(DiagnosticResult(file, diagnostics))
  }
}
