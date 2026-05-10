package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.api.ILanguageClient
import org.json.JSONObject

object ClangdDiagnosticsBridge {
  fun publish(client: ILanguageClient?, params: JSONObject) = ClangdDiagnosticsManager.publish(client, params)
}
