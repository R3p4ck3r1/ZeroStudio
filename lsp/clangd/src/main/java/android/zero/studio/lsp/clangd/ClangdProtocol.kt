package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Location
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import org.json.JSONArray
import org.json.JSONObject

internal object ClangdProtocol {
  fun uri(path: Path): String = path.toFile().toURI().toString()
  fun path(uri: String): Path = Paths.get(URI(uri))
  fun pos(position: Position) = JSONObject().put("line", position.line).put("character", position.column)
  fun range(range: Range) = JSONObject().put("start", pos(range.start)).put("end", pos(range.end))
  fun textDocument(path: Path) = JSONObject().put("uri", uri(path))
  fun textPosition(path: Path, position: Position) = JSONObject().put("textDocument", textDocument(path)).put("position", pos(position))

  fun toPosition(obj: JSONObject?) = Position(obj?.optInt("line", 0) ?: 0, obj?.optInt("character", 0) ?: 0)
  fun toRange(obj: JSONObject?) = Range(toPosition(obj?.optJSONObject("start")), toPosition(obj?.optJSONObject("end")))
  fun toLocation(obj: JSONObject): Location = Location(path(obj.optString("uri")), toRange(obj.optJSONObject("range")))

  fun toMarkup(value: Any?): MarkupContent = when (value) {
    is JSONObject -> MarkupContent(value.optString("value", ""), if (value.optString("kind") == "markdown") MarkupKind.MARKDOWN else MarkupKind.PLAIN)
    is JSONArray -> MarkupContent((0 until value.length()).joinToString("\n") { value.optString(it) }, MarkupKind.PLAIN)
    is String -> MarkupContent(value, MarkupKind.PLAIN)
    else -> MarkupContent()
  }

  fun completionKind(kind: Int): CompletionItemKind = when (kind) {
    2 -> CompletionItemKind.METHOD
    3 -> CompletionItemKind.FUNCTION
    4 -> CompletionItemKind.CONSTRUCTOR
    5 -> CompletionItemKind.FIELD
    6 -> CompletionItemKind.VARIABLE
    7 -> CompletionItemKind.CLASS
    8 -> CompletionItemKind.INTERFACE
    9 -> CompletionItemKind.MODULE
    10 -> CompletionItemKind.PROPERTY
    12 -> CompletionItemKind.VALUE
    13 -> CompletionItemKind.ENUM
    14 -> CompletionItemKind.KEYWORD
    15 -> CompletionItemKind.SNIPPET
    25 -> CompletionItemKind.TYPE_PARAMETER
    else -> CompletionItemKind.NONE
  }

  fun symbolKind(kind: Int): SymbolKind = SymbolKind.values().getOrElse((kind - 1).coerceAtLeast(0)) { SymbolKind.Null }

  fun textEdit(obj: JSONObject?): TextEdit? = obj?.let { TextEdit(toRange(it.optJSONObject("range")), it.optString("newText", "")) }

  fun command(obj: JSONObject?): Command? = obj?.let {
    Command(it.optString("title", it.optString("command", "")), it.optString("command", ""), jsonArrayToList(it.optJSONArray("arguments")))
  }

  fun locations(result: Any?): List<Location> = when (result) {
    is JSONArray -> result.objects().map(::toLocation)
    is JSONObject -> listOf(toLocation(result))
    else -> emptyList()
  }

  fun workspaceEdit(obj: JSONObject?): WorkspaceEdit {
    if (obj == null) return WorkspaceEdit()
    val changes = mutableListOf<DocumentChange>()
    obj.optJSONObject("changes")?.let { map ->
      map.keys().forEach { uri ->
        val edits = map.optJSONArray(uri).objects().mapNotNull(::textEdit)
        changes += DocumentChange(path(uri), edits)
      }
    }
    obj.optJSONArray("documentChanges")?.objects()?.forEach { change ->
      val textDocument = change.optJSONObject("textDocument") ?: return@forEach
      val uri = textDocument.optString("uri", "")
      if (uri.isBlank()) return@forEach
      changes += DocumentChange(path(uri), change.optJSONArray("edits").objects().mapNotNull(::textEdit))
    }
    return WorkspaceEdit(changes)
  }

  fun codeAction(obj: JSONObject): CodeActionItem {
    val kind = when (obj.optString("kind", "")) {
      "quickfix" -> CodeActionKind.QuickFix
      "refactor" -> CodeActionKind.Refactor
      "refactor.extract" -> CodeActionKind.RefactorExtract
      "refactor.inline" -> CodeActionKind.RefactorInline
      "refactor.rewrite" -> CodeActionKind.RefactorRewrite
      "source" -> CodeActionKind.Source
      "source.organizeImports" -> CodeActionKind.SourceOrganizeImports
      "source.fixAll" -> CodeActionKind.SourceFixAll
      else -> CodeActionKind.None
    }
    val disabled = obj.optJSONObject("disabled")?.optString("reason")
    return CodeActionItem(
        title = obj.optString("title", ""),
        changes = workspaceEdit(obj.optJSONObject("edit")).documentChanges,
        kind = kind,
        command = command(obj.optJSONObject("command")),
        isPreferred = obj.optBoolean("isPreferred", false),
        disabledReason = disabled,
        data = obj.opt("data"),
    )
  }

  fun diagnostic(item: DiagnosticItem): JSONObject = JSONObject()
      .put("range", range(item.range))
      .put("message", item.message)
      .put("source", item.source)
      .put("code", item.code)
      .put("severity", when (item.severity) {
        DiagnosticSeverity.ERROR -> 1
        DiagnosticSeverity.WARNING -> 2
        DiagnosticSeverity.INFO -> 3
        DiagnosticSeverity.HINT -> 4
      })

  fun diagnostic(obj: JSONObject): DiagnosticItem = DiagnosticItem(
      message = obj.optString("message", ""),
      code = obj.opt("code")?.toString().orEmpty(),
      range = toRange(obj.optJSONObject("range")),
      source = obj.optString("source", "clangd"),
      severity = when (obj.optInt("severity", 3)) {
        1 -> DiagnosticSeverity.ERROR
        2 -> DiagnosticSeverity.WARNING
        4 -> DiagnosticSeverity.HINT
        else -> DiagnosticSeverity.INFO
      },
  )

  fun jsonArray(items: Iterable<Any?>) = JSONArray().also { array -> items.forEach { array.put(it) } }

  private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
  private fun jsonArrayToList(array: JSONArray?): List<Any?>? = array?.let { (0 until it.length()).map { index -> it.opt(index) } }
}
