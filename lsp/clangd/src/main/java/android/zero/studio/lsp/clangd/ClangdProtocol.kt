package android.zero.studio.lsp.clangd

import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Location
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import java.nio.file.Path
import java.nio.file.Paths
import org.json.JSONArray
import org.json.JSONObject

internal object ClangdProtocol {
  fun uri(path: Path): String = path.toFile().toURI().toString()
  fun path(uri: String): Path = Paths.get(java.net.URI(uri))
  fun pos(position: Position) = JSONObject().put("line", position.line).put("character", position.column)
  fun range(range: Range) = JSONObject().put("start", pos(range.start)).put("end", pos(range.end))
  fun textDocument(path: Path) = JSONObject().put("uri", uri(path))
  fun textPosition(path: Path, position: Position) = JSONObject().put("textDocument", textDocument(path)).put("position", pos(position))

  fun toPosition(obj: JSONObject?) = Position(obj?.optInt("line", 0) ?: 0, obj?.optInt("character", 0) ?: 0)
  fun toRange(obj: JSONObject?) = Range(toPosition(obj?.optJSONObject("start")), toPosition(obj?.optJSONObject("end")))
  fun toLocation(obj: JSONObject): Location = Location(path(obj.optString("uri")), toRange(obj.optJSONObject("range")))

  fun toMarkup(value: Any?): MarkupContent = when (value) {
    is JSONObject -> MarkupContent(value.optString("value", ""), if (value.optString("kind") == "markdown") MarkupKind.MARKDOWN else MarkupKind.PLAIN)
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

  fun locations(result: Any?): List<Location> = when (result) {
    is JSONArray -> (0 until result.length()).mapNotNull { result.optJSONObject(it) }.map(::toLocation)
    is JSONObject -> listOf(toLocation(result))
    else -> emptyList()
  }

  fun jsonArray(items: Iterable<Any?>) = JSONArray().also { array -> items.forEach { array.put(it) } }
}
