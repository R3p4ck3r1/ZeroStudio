/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.api.IServerSettings
import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Location
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.projects.IWorkspace
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 适配 AndroidIDE 的全新纯本地化 Clangd Language Server 核心。
 *
 * 核心设计与规范说明：
 * 1. 使用 companion object 规范声明 SERVER_ID，对齐 AndroidIDE 内置 LanguageServer 风格。
 * 2. 彻底移除了 JNI，使用 ProcessBuilder 和 JSON-RPC Messenger 进行纯粹的双向通信。
 * 3. 完整支持 `ClangdAdvancedFeatureProvider`。
 *
 * @author android_zero
 */
class ClangdLanguageServer : ILanguageServer {

    companion object {
        const val SERVER_ID = "ide.lsp.clangd"
        private val log = LoggerFactory.getLogger(ClangdLanguageServer::class.java)
        private const val SYNC_TIMEOUT_MS = 2500L
    }

    override val serverId: String = SERVER_ID
    
    override var client: ILanguageClient? = null
        private set

    private var processConnection: TermuxClangdConnection? = null
    private var rpcMessenger: JsonRpcMessenger? = null
    private var advancedProvider: ClangdAdvancedFeatureProvider? = null
    
    private var serverSettings = ClangdServerSettings()
    private var workspace: IWorkspace? = null

    override fun connectClient(client: ILanguageClient?) {
        this.client = client
    }

    override fun applySettings(settings: IServerSettings?) {
        if (settings is ClangdServerSettings) {
            this.serverSettings = settings
        }
    }

    override fun setupWorkspace(workspace: IWorkspace) {
        this.workspace = workspace
        val projectDir = workspace.getProjectDir()
        
        try {
            // 1. 智能解析出系统级 sysroot, bin 及 clangd 所在路径
            val toolchain = ClangdSysrootResolver.resolve(projectDir)

            // 2. 智能定位或生成编译数据库
            val compileCommandsDir = CompileDatabaseProvider.ensureCompileCommandsDir(projectDir, toolchain)

            // 3. 构造参数启动子进程
            val args = serverSettings.clangdSettings.buildCommandArgs(compileCommandsDir)
            processConnection = TermuxClangdConnection(projectDir, toolchain.clangdExecutable, args).apply {
                start()
            }

            // 4. 建立通信管道
            rpcMessenger = JsonRpcMessenger(
                processConnection!!.inputStream,
                processConnection!!.outputStream
            ) { method, params -> 
                handleServerNotification(method, params)
            }.apply {
                start()
            }

            advancedProvider = ClangdAdvancedFeatureProvider(rpcMessenger!!)

            // 5. 初始化协议 (声明所有进阶功能支持)
            sendInitializeRequest(projectDir)

        } catch (e: Exception) {
            log.error("Failed to setup Clangd workspace", e)
        }
    }

    private fun handleServerNotification(method: String, params: JSONObject) {
        if (method == "textDocument/publishDiagnostics" && serverSettings.diagnosticsEnabled()) {
            ClangdDiagnosticsManager.processDiagnostics(client, params)
        }
    }

    private fun sendInitializeRequest(projectDir: File) {
        // 向 Clangd 声明客户端能处理这些功能
        val capabilities = JSONObject().apply {
            put("textDocument", JSONObject().apply {
                put("completion", JSONObject().put("completionItem", JSONObject().put("snippetSupport", true)))
                put("hover", JSONObject())
                put("definition", JSONObject())
                put("references", JSONObject())
                put("formatting", JSONObject())
                put("documentSymbol", JSONObject().apply {
                    put("hierarchicalDocumentSymbolSupport", true)
                })
                put("semanticTokens", JSONObject().apply {
                    put("requests", JSONObject().put("full", true).put("range", true))
                    put("tokenTypes", JSONArray(listOf("namespace", "type", "class", "enum", "interface", "struct", "typeParameter", "parameter", "variable", "property", "enumMember", "event", "function", "method", "macro", "keyword", "modifier", "comment", "string", "number", "regexp", "operator")))
                    put("tokenModifiers", JSONArray(listOf("declaration", "definition", "readonly", "static", "deprecated", "abstract", "async", "modification", "documentation", "defaultLibrary")))
                    put("formats", JSONArray(listOf("relative")))
                })
                put("inlayHint", JSONObject())
                put("signatureHelp", JSONObject())
                put("rename", JSONObject().put("prepareSupport", true))
                put("codeAction", JSONObject().apply {
                    put("codeActionLiteralSupport", JSONObject().put("codeActionKind", JSONObject().put("valueSet", JSONArray(listOf("quickfix", "refactor")))))
                })
            })
            put("workspace", JSONObject().apply {
                put("workspaceEdit", JSONObject().put("documentChanges", true))
            })
        }

        val params = JSONObject().apply {
            put("processId", android.os.Process.myPid())
            put("rootUri", projectDir.toURI().toString())
            put("capabilities", capabilities)
        }

        rpcMessenger?.sendRequest("initialize", params)?.thenAccept { _ ->
            rpcMessenger?.sendNotification("initialized", JSONObject())
            log.info("Clangd server fully initialized and running with Termux sandbox and Advanced Features.")
        }
    }

    override fun shutdown() {
        log.info("Shutting down Clangd Language Server...")
        try {
            rpcMessenger?.sendRequest("shutdown", JSONObject())?.get(1, TimeUnit.SECONDS)
            rpcMessenger?.sendNotification("exit", JSONObject())
        } catch (e: Exception) {
            // Ignore timeout
        } finally {
            rpcMessenger?.shutdown()
            processConnection?.close()
        }
    }

    // --- 文档生命周期代理 ---
    
    override fun didOpen(params: DidOpenTextDocumentParams) {
        val docObj = JSONObject().apply {
            put("uri", params.file.toUri().toString())
            put("languageId", params.languageId)
            put("version", params.version)
            put("text", params.text)
        }
        rpcMessenger?.sendNotification("textDocument/didOpen", JSONObject().put("textDocument", docObj))
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val text = params.contentChanges.lastOrNull()?.text ?: return
        val docObj = JSONObject().put("uri", params.file.toUri().toString()).put("version", params.version)
        val changeObj = JSONObject().put("text", text)
        val req = JSONObject().put("textDocument", docObj).put("contentChanges", JSONArray().put(changeObj))
        rpcMessenger?.sendNotification("textDocument/didChange", req)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val docObj = JSONObject().put("uri", params.file.toUri().toString())
        rpcMessenger?.sendNotification("textDocument/didClose", JSONObject().put("textDocument", docObj))
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        val docObj = JSONObject().put("uri", params.file.toUri().toString())
        rpcMessenger?.sendNotification("textDocument/didSave", JSONObject().put("textDocument", docObj))
    }

    // --- 核心服务与分发 ---

    override fun complete(params: CompletionParams?): CompletionResult {
        if (params == null || !serverSettings.completionsEnabled() || rpcMessenger == null) {
            return CompletionResult.EMPTY
        }
        val requestParams = buildTextDocumentPositionParams(params.file.toUri().toString(), params.position)
        
        try {
            val future = rpcMessenger!!.sendRequest("textDocument/completion", requestParams)
            var waitedMs = 0L
            while (!future.isDone && waitedMs < SYNC_TIMEOUT_MS) {
                if (params.cancelChecker.isCancelled) {
                    future.cancel(true)
                    return CompletionResult.EMPTY
                }
                Thread.sleep(20)
                waitedMs += 20
            }
            if (!future.isDone) return CompletionResult.EMPTY

            val response = future.get()
            return parseCompletionResult(response, params.requirePrefix())
        } catch (e: Exception) {
            return CompletionResult.EMPTY
        }
    }

    override suspend fun findDefinition(params: DefinitionParams): DefinitionResult {
        if (!serverSettings.definitionsEnabled() || rpcMessenger == null) return DefinitionResult(emptyList())
        return withContext(Dispatchers.IO) {
            val req = buildTextDocumentPositionParams(params.file.toUri().toString(), params.position)
            try {
                val response = rpcMessenger!!.sendRequest("textDocument/definition", req).get(3, TimeUnit.SECONDS)
                DefinitionResult(parseLocations(response))
            } catch (e: Exception) {
                DefinitionResult(emptyList())
            }
        }
    }

    override suspend fun findReferences(params: ReferenceParams): ReferenceResult {
        if (!serverSettings.referencesEnabled() || rpcMessenger == null) return ReferenceResult(emptyList())
        return withContext(Dispatchers.IO) {
            val req = buildTextDocumentPositionParams(params.file.toUri().toString(), params.position).apply {
                put("context", JSONObject().put("includeDeclaration", params.includeDeclaration))
            }
            try {
                val response = rpcMessenger!!.sendRequest("textDocument/references", req).get(5, TimeUnit.SECONDS)
                ReferenceResult(parseLocations(response))
            } catch (e: Exception) {
                ReferenceResult(emptyList())
            }
        }
    }

    override suspend fun hover(params: DefinitionParams): MarkupContent {
        if (rpcMessenger == null) return MarkupContent()
        return withContext(Dispatchers.IO) {
            val req = buildTextDocumentPositionParams(params.file.toUri().toString(), params.position)
            try {
                val response = rpcMessenger!!.sendRequest("textDocument/hover", req).get(2, TimeUnit.SECONDS)
                val resultObj = response.optJSONObject("result") ?: return@withContext MarkupContent()
                val contents = resultObj.opt("contents")
                val value = when(contents) {
                    is String -> contents
                    is JSONObject -> contents.optString("value", "")
                    is JSONArray -> (0 until contents.length()).mapNotNull { i -> 
                        val item = contents.opt(i)
                        if (item is String) item else (item as? JSONObject)?.optString("value", "") 
                    }.joinToString("\n").trim()
                    else -> ""
                }
                MarkupContent(value, MarkupKind.MARKDOWN)
            } catch (e: Exception) {
                MarkupContent()
            }
        }
    }

    // --- 高级功能分发 ---

    override fun formatCode(params: FormatCodeParams?): CodeFormatResult {
        if (params == null || advancedProvider == null) return CodeFormatResult.NONE
        
        // AndroidIDE 默认未在 FormatCodeParams 提供文档上下文，因此假定这里用 placeholder。
        // 如后续系统开放 file path 支持，可直接使用。
        val fileUri = "file:///dummy.cpp"
        return advancedProvider!!.formatCodeSync(params, fileUri)
    }

    override suspend fun documentSymbols(file: Path): DocumentSymbolsResult {
        if (advancedProvider == null) return DocumentSymbolsResult()
        return advancedProvider!!.documentSymbols(file.toUri().toString())
    }

    override suspend fun semanticTokensFull(params: SemanticTokensParams): SemanticTokens {
        if (advancedProvider == null) return SemanticTokens()
        return advancedProvider!!.semanticTokensFull(params)
    }

    override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp {
        if (advancedProvider == null || !serverSettings.signatureHelpEnabled()) return SignatureHelp(emptyList(), 0, 0)
        return advancedProvider!!.signatureHelp(params)
    }

    override suspend fun inlayHints(params: InlayHintParams): List<InlayHint> {
        if (advancedProvider == null) return emptyList()
        return advancedProvider!!.inlayHints(params)
    }

    override suspend fun rename(params: RenameParams): WorkspaceEdit {
        if (advancedProvider == null) return WorkspaceEdit(emptyList())
        return advancedProvider!!.rename(params)
    }

    /**
     * 将获取到的 CodeAction 提供给客户端请求执行
     */
    suspend fun getCodeActions(file: Path, range: com.itsaky.androidide.models.Range, diagnostics: List<DiagnosticItem>): List<CodeActionItem> {
        if (advancedProvider == null || !serverSettings.codeActionsEnabled()) return emptyList()
        return advancedProvider!!.codeActions(file.toUri().toString(), range, diagnostics)
    }

    override suspend fun analyze(file: Path): DiagnosticResult {
        return DiagnosticResult.NO_UPDATE
    }

    // --- 内部数据解析 ---

    private fun buildTextDocumentPositionParams(uri: String, position: Position): JSONObject {
        return JSONObject().apply {
            put("textDocument", JSONObject().put("uri", uri))
            put("position", JSONObject().apply {
                put("line", position.line)
                put("character", position.column)
            })
        }
    }

    private fun parseCompletionResult(response: JSONObject, prefix: String): CompletionResult {
        val resultObj = response.optJSONObject("result") ?: return CompletionResult.EMPTY
        val itemsArray = resultObj.optJSONArray("items") ?: return CompletionResult.EMPTY
        val completionResult = CompletionResult().apply { isIncomplete = resultObj.optBoolean("isIncomplete", false) }
        val minRatio = serverSettings.completionFuzzyMatchMinRatio()

        for (i in 0 until itemsArray.length()) {
            val itemObj = itemsArray.optJSONObject(i) ?: continue
            val label = itemObj.optString("label", "")
            val matchLevel = CompletionItem.matchLevel(label, prefix, minRatio)
            if (matchLevel == MatchLevel.NO_MATCH) continue
            
            val item = CompletionItem().apply {
                ideLabel = label
                detail = itemObj.optString("detail", "")
                insertText = itemObj.optJSONObject("textEdit")?.optString("newText") ?: itemObj.optString("insertText", label)
                completionKind = mapCompletionItemKind(itemObj.optInt("kind", 1))
                this.matchLevel = matchLevel
                ideSortText = itemObj.optString("sortText", label)
                insertTextFormat = if (itemObj.optInt("insertTextFormat", 1) == 2) InsertTextFormat.SNIPPET else InsertTextFormat.PLAIN_TEXT
            }
            completionResult.add(item)
        }
        return completionResult
    }

    private fun parseLocations(response: JSONObject): List<Location> {
        val locations = mutableListOf<Location>()
        val result = response.opt("result") ?: return locations
        if (result is JSONArray) {
            for (i in 0 until result.length()) {
                parseSingleLocation(result.optJSONObject(i))?.let { locations.add(it) }
            }
        } else if (result is JSONObject) {
            parseSingleLocation(result)?.let { locations.add(it) }
        }
        return locations
    }

    private fun parseSingleLocation(obj: JSONObject?): Location? {
        if (obj == null) return null
        val uriStr = obj.optString("uri", "")
        if (uriStr.isEmpty()) return null
        val filePath = if (uriStr.startsWith("file://")) uriStr.substring(7) else uriStr
        val startObj = obj.optJSONObject("range")?.optJSONObject("start") ?: return null
        val endObj = obj.optJSONObject("range")?.optJSONObject("end") ?: return null
        
        return Location(
            File(filePath),
            startObj.optInt("line", 0), startObj.optInt("character", 0),
            endObj.optInt("line", 0), endObj.optInt("character", 0)
        )
    }

    private fun mapCompletionItemKind(kind: Int): CompletionItemKind {
        return when (kind) {
            1 -> CompletionItemKind.VALUE
            2, 3 -> CompletionItemKind.METHOD
            4 -> CompletionItemKind.CONSTRUCTOR
            5, 6 -> CompletionItemKind.FIELD
            7 -> CompletionItemKind.CLASS
            8 -> CompletionItemKind.INTERFACE
            9 -> CompletionItemKind.MODULE
            10 -> CompletionItemKind.PROPERTY
            13 -> CompletionItemKind.ENUM
            14 -> CompletionItemKind.KEYWORD
            15 -> CompletionItemKind.SNIPPET
            20 -> CompletionItemKind.ENUM_MEMBER
            else -> CompletionItemKind.NONE
        }
    }
}