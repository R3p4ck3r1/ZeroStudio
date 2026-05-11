package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.api.IServerSettings
import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.projects.IWorkspace
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory

class ClangdLanguageServer : ILanguageServer {
  companion object { const val SERVER_ID = "ide.lsp.clangd" }

  private val log = LoggerFactory.getLogger(javaClass)
  private var connection: TermuxClangdConnection? = null
  private var rpc: JsonRpcMessenger? = null
  private var features: ClangdAdvancedFeatureProvider? = null
  private var settings = ClangdServerSettings()
  private var workspaceRoot: java.io.File? = null
  private val documents = mutableMapOf<Path, String>()
  private val publishedDiagnostics = mutableMapOf<Path, List<DiagnosticItem>>()
  private var lastActiveFile: Path? = null

  override val serverId: String = SERVER_ID
  override var client: ILanguageClient? = null
    private set

  override fun connectClient(client: ILanguageClient?) { this.client = client }

  override fun applySettings(settings: IServerSettings?) {
    this.settings = settings as? ClangdServerSettings ?: ClangdServerSettings()
  }

  override fun setupWorkspace(workspace: IWorkspace) {
    workspaceRoot = workspace.getProjectDir()
    restart()
  }

  private fun restart() {
    shutdown()
    val root = workspaceRoot ?: return
    try {
      val toolchain = ClangdSysrootResolver.resolve(root, settings)
      val compileCommandsDir = CompileDatabaseProvider.ensureCompileCommandsDir(root, toolchain)
      val args = settings.clangdSettings.buildCommandArgs(compileCommandsDir, toolchain)
      val startedConnection = TermuxClangdConnection(root, toolchain, args).also { it.start() }
      val messenger = JsonRpcMessenger(startedConnection.inputStream, startedConnection.outputStream) { method, params ->
        when (method) {
          "textDocument/publishDiagnostics" -> if (settings.diagnosticsEnabled()) publishDiagnostics(params)
          "window/logMessage" -> client?.logMessage(LogMessageParams(messageType(params.optInt("type", 3)), params.optString("message", "")))
          "window/showMessage" -> client?.showMessage(ShowMessageParams(messageType(params.optInt("type", 3)), params.optString("message", "")))
        }
      }.also { it.start() }
      connection = startedConnection
      rpc = messenger
      features = ClangdAdvancedFeatureProvider(messenger)
      initialize(root)
      documents.forEach { (path, text) -> didOpen(DidOpenTextDocumentParams(path, languageId(path), 1, text)) }
    } catch (t: Throwable) {
      log.error("Unable to start clangd", t)
      client?.showMessage(
          ShowMessageParams(
              MessageType.Error,
              "clangd 启动失败: ${t.message ?: t.javaClass.simpleName}",
          ))
    }
  }

  private fun initialize(root: java.io.File) {
    val capabilities = JSONObject()
        .put("textDocument", JSONObject()
            .put("completion", JSONObject().put("completionItem", JSONObject().put("snippetSupport", true)))
            .put("hover", JSONObject())
            .put("definition", JSONObject())
            .put("references", JSONObject())
            .put("signatureHelp", JSONObject())
            .put("documentSymbol", JSONObject().put("hierarchicalDocumentSymbolSupport", true))
            .put("formatting", JSONObject())
            .put("inlayHint", JSONObject())
            .put("semanticTokens", JSONObject()
                .put("requests", JSONObject().put("full", true).put("range", true))
                .put("formats", JSONArray().put("relative"))
                .put("tokenTypes", JSONArray(listOf("namespace", "type", "class", "enum", "interface", "struct", "typeParameter", "parameter", "variable", "property", "enumMember", "event", "function", "method", "macro", "keyword", "modifier", "comment", "string", "number", "regexp", "operator")))
                .put("tokenModifiers", JSONArray(listOf("declaration", "definition", "readonly", "static", "deprecated", "abstract", "async", "modification", "documentation", "defaultLibrary")))))
        .put("workspace", JSONObject().put("workspaceFolders", true).put("configuration", true))
    val params = JSONObject()
        .put("processId", android.os.Process.myPid())
        .put("rootUri", root.toURI().toString())
        .put("capabilities", capabilities)
        .put("workspaceFolders", JSONArray().put(JSONObject().put("uri", root.toURI().toString()).put("name", root.name)))
    rpc?.sendRequest("initialize", params)?.get(10, TimeUnit.SECONDS)
    rpc?.sendNotification("initialized", JSONObject())
  }

  override fun shutdown() {
    runCatching { rpc?.sendRequest("shutdown", JSONObject())?.get(1, TimeUnit.SECONDS) }
    runCatching { rpc?.sendNotification("exit", JSONObject()) }
    runCatching { rpc?.close() }
    runCatching { connection?.close() }
    rpc = null
    connection = null
    features = null
  }

  override fun didOpen(params: DidOpenTextDocumentParams) {
    lastActiveFile = params.file
    documents[params.file] = params.text
    rpc?.sendNotification("textDocument/didOpen", JSONObject().put("textDocument", JSONObject().put("uri", ClangdProtocol.uri(params.file)).put("languageId", params.languageId).put("version", params.version).put("text", params.text)))
  }

  override fun didChange(params: DidChangeTextDocumentParams) {
    lastActiveFile = params.file
    val text = params.contentChanges.lastOrNull()?.text ?: return
    documents[params.file] = text
    rpc?.sendNotification("textDocument/didChange", JSONObject().put("textDocument", JSONObject().put("uri", ClangdProtocol.uri(params.file)).put("version", params.version)).put("contentChanges", JSONArray().put(JSONObject().put("text", text))))
  }

  override fun didClose(params: DidCloseTextDocumentParams) {
    documents.remove(params.file)
    publishedDiagnostics.remove(params.file)
    rpc?.sendNotification("textDocument/didClose", JSONObject().put("textDocument", ClangdProtocol.textDocument(params.file)))
  }

  override fun didSave(params: DidSaveTextDocumentParams) {
    lastActiveFile = params.file
    params.text?.let { documents[params.file] = it }
    rpc?.sendNotification("textDocument/didSave", JSONObject().put("textDocument", ClangdProtocol.textDocument(params.file)).also { if (params.text != null) it.put("text", params.text) })
  }

  override fun complete(params: CompletionParams?): CompletionResult {
    if (params == null || !settings.completionsEnabled()) return CompletionResult.EMPTY
    return runCatching {
      val result = requireRpc().sendRequest("textDocument/completion", ClangdProtocol.textPosition(params.file, params.position)).get(5, TimeUnit.SECONDS).opt("result")
      val items = when (result) {
        is JSONArray -> result
        is JSONObject -> result.optJSONArray("items") ?: JSONArray()
        else -> JSONArray()
      }
      CompletionResult((0 until items.length()).mapNotNull { items.optJSONObject(it) }.map { item ->
        val textEdit = ClangdProtocol.textEdit(item.optJSONObject("textEdit"))
        val additionalEdits = item.optJSONArray("additionalTextEdits")?.let { edits ->
          (0 until edits.length()).mapNotNull { index -> ClangdProtocol.textEdit(edits.optJSONObject(index)) }
        }.orEmpty()
        CompletionItem(
            item.optString("label", ""),
            item.optString("detail", item.optString("documentation", "")),
            textEdit?.newText ?: item.optString("insertText", item.optString("label", "")),
            if (item.optInt("insertTextFormat", 1) == 2) InsertTextFormat.SNIPPET else InsertTextFormat.PLAIN_TEXT,
            if (item.has("sortText")) item.optString("sortText") else null,
            ClangdProtocol.command(item.optJSONObject("command")),
            ClangdProtocol.completionKind(item.optInt("kind", 0)),
            MatchLevel.CASE_INSENSITIVE_PREFIX,
            additionalEdits,
            null,
        )
      })
    }.getOrElse { log.warn("clangd completion failed", it); CompletionResult.EMPTY }
  }

  override suspend fun findReferences(params: ReferenceParams) = ReferenceResult(requestLocations("textDocument/references", ClangdProtocol.textPosition(params.file, params.position).put("context", JSONObject().put("includeDeclaration", params.includeDeclaration))))
  override suspend fun findDefinition(params: DefinitionParams) = DefinitionResult(requestLocations("textDocument/definition", ClangdProtocol.textPosition(params.file, params.position)))
  override suspend fun hover(params: DefinitionParams) = runCatching { requireFeatures().hover(params) }.getOrDefault(MarkupContent())
  override suspend fun signatureHelp(params: SignatureHelpParams) = runCatching { requireFeatures().signatureHelp(params) }.getOrDefault(SignatureHelp(emptyList(), 0, 0))
  override suspend fun analyze(file: Path) = DiagnosticResult(file, publishedDiagnostics[file].orEmpty())

  override fun supportsDocumentDiagnostics(): Boolean = true

  override suspend fun documentDiagnostics(params: DocumentDiagnosticParams): DocumentDiagnosticReport = DocumentDiagnosticReport(
      kind = DocumentDiagnosticReportKind.FULL,
      diagnostics = publishedDiagnostics[params.file].orEmpty(),
  )

  override fun formatCode(params: FormatCodeParams?): CodeFormatResult {
    val file = lastActiveFile ?: return CodeFormatResult.NONE
    return runCatching { CodeFormatResult(false, requireFeatures().format(file).toMutableList(), mutableListOf()) }.getOrDefault(CodeFormatResult.NONE)
  }
  override suspend fun documentSymbols(file: Path) = runCatching { requireFeatures().documentSymbols(file) }.getOrDefault(DocumentSymbolsResult())
  override suspend fun prepareRename(params: DefinitionParams): PrepareRenameResult? = runCatching { requireFeatures().prepareRename(params) }.getOrNull()
  override suspend fun rename(params: RenameParams): WorkspaceEdit = runCatching { requireFeatures().rename(params) }.getOrDefault(WorkspaceEdit())
  override suspend fun foldingRanges(file: Path): List<FoldingRange> = runCatching { requireFeatures().foldingRanges(file) }.getOrDefault(emptyList())
  override suspend fun selectionRanges(params: SelectionRangesParams): List<SelectionRange> = runCatching { requireFeatures().selectionRanges(params) }.getOrDefault(emptyList())
  override suspend fun semanticTokensFull(params: SemanticTokensParams) = runCatching { requireFeatures().semanticTokensFull(params) }.getOrDefault(SemanticTokens())
  override suspend fun semanticTokensRange(params: SemanticTokensParams) = runCatching { requireFeatures().semanticTokensRange(params) }.getOrDefault(SemanticTokens())
  override suspend fun inlayHints(params: InlayHintParams) = runCatching { requireFeatures().inlayHints(params) }.getOrDefault(emptyList())
  override suspend fun documentLinks(file: Path): List<DocumentLink> = runCatching { requireFeatures().documentLinks(file) }.getOrDefault(emptyList())
  override suspend fun codeLens(file: Path): List<CodeLens> = runCatching { requireFeatures().codeLens(file) }.getOrDefault(emptyList())
  override suspend fun executeCommand(params: ExecuteCommandParams): Any? = runCatching { requireFeatures().executeCommand(params) }.getOrNull()
  override suspend fun resolveCodeAction(action: CodeActionItem): CodeActionItem = runCatching { requireFeatures().resolveCodeAction(action) }.getOrDefault(action)
  override suspend fun expandSelection(params: ExpandSelectionParams): Range = params.selection

  private fun requestLocations(method: String, params: JSONObject) = runCatching { ClangdProtocol.locations(requireRpc().sendRequest(method, params).get(5, TimeUnit.SECONDS).opt("result")) }.getOrDefault(emptyList())

  private fun publishDiagnostics(params: JSONObject) {
    val uri = params.optString("uri", "")
    val file = runCatching { ClangdProtocol.path(uri) }.getOrNull() ?: return
    val diagnostics = ClangdDiagnosticsManager.parse(file, params)
    publishedDiagnostics[file] = diagnostics
    client?.publishDiagnostics(DiagnosticResult(file, diagnostics))
  }
  private fun messageType(type: Int): MessageType = when (type) { 1 -> MessageType.Error; 2 -> MessageType.Warning; 4 -> MessageType.Log; 5 -> MessageType.Debug; else -> MessageType.Info }
  private fun requireRpc() = rpc ?: throw IllegalStateException("clangd is not running")
  private fun requireFeatures() = features ?: throw IllegalStateException("clangd is not running")
  private fun languageId(path: Path): String = if (path.toString().endsWith(".c")) "c" else "cpp"
}
