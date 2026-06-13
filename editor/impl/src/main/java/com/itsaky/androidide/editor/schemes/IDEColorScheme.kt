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

package com.itsaky.androidide.editor.schemes

import androidx.collection.MutableIntIntMap
import com.itsaky.androidide.editor.schemes.internal.parser.SchemeParser
import com.itsaky.androidide.syntax.colorschemes.DynamicColorScheme
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.File
import java.util.TreeSet

class IDEColorScheme(internal val file: File, val key: String) : DynamicColorScheme() {

  internal val colorIds = MutableIntIntMap()
  internal val editorScheme = MutableIntIntMap()
  internal val languages = mutableMapOf<String, LanguageScheme>()

  var name: String = ""
    internal set

  var version: Int = 0
    internal set

  var isDarkScheme: Boolean = false
    internal set

  var darkVariant: IDEColorScheme? = null
    internal set

  var definitions: Map<String, Int> = emptyMap()
    internal set

  private var colorId = endColorId

  /**
   * Whether this scheme has already been loaded.
   *
   * Loading the same scheme multiple times would otherwise cause the internal data structures
   * ([colorIds], [editorScheme], [languages]) to grow without bound and the [colorId] counter to
   * monotonically increase, leading to unbounded memory growth. This flag guards [load] so that
   * repeated calls become a no-op. Use [reset] to clear the loaded state before calling [load]
   * again.
   */
  internal var isLoaded: Boolean = false
    private set

  /**
   * Parse and load this color scheme from [file].
   *
   * This method is idempotent: if the scheme has already been loaded ([isLoaded] is `true`), the
   * call is a no-op. This prevents the unbounded memory growth that would otherwise occur when
   * [load] is called repeatedly for the same scheme instance (e.g. every time a file is opened
   * and its language's color scheme is requested).
   *
   * To explicitly reload the scheme (e.g. after the scheme file has been edited on disk), call
   * [reset] first.
   *
   * If a previous load attempt failed partway through parsing, this method will internally
   * [reset] the scheme first to discard the partial state before re-parsing. This ensures that
   * a failed load never leaves the scheme in a half-populated state that would cause entries
   * to be appended on the next successful load.
   *
   * The method is synchronized so that concurrent callers (e.g. multiple Kotlin coroutines on
   * `Dispatchers.IO`) cannot race to populate the internal data structures.
   */
  @Synchronized
  internal fun load() {
    if (isLoaded) {
      // Idempotent guard: repeated loads of the same scheme would otherwise accumulate entries
      // in colorIds/editorScheme/languages and monotonically grow colorId, leaking memory.
      return
    }
    // Discard any state left over from a previous failed load attempt so that we never append
    // to half-populated maps. When this is the very first load, reset() is a cheap no-op since
    // all the structures are already empty.
    if (colorIds.size > 0 || editorScheme.size > 0 || languages.isNotEmpty() ||
        definitions.isNotEmpty()) {
      reset()
    }
    SchemeParser { name -> File(this.file.parentFile, name) }.load(this)
    isLoaded = true
  }

  /**
   * Reset the loaded state of this color scheme. After calling this method, [isLoaded] becomes
   * `false` and the next call to [load] will re-parse [file] from disk.
   *
   * All accumulated data ([colorIds], [editorScheme], [languages], [definitions]) is cleared and
   * [colorId] is reset to its initial value. Any external references to previously-loaded
   * [LanguageScheme] instances should be discarded by callers, as those objects are no longer
   * reachable through this scheme.
   *
   * Synchronized to coordinate with [load].
   */
  @Synchronized
  internal fun reset() {
    colorIds.clear()
    editorScheme.clear()
    languages.clear()
    definitions = emptyMap()
    colorId = endColorId
    isLoaded = false
  }

  fun getLanguageScheme(type: String): LanguageScheme? {
    return this.languages[type]
  }

  internal fun putColor(color: Int): Int {
    this.colorIds[++colorId] = color
    return colorId
  }

  @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
  override fun getColor(type: Int): Int {
    // getColor is called in superclass constructor
    // in this case, the below properties will be null
    val defaultValue = super.getColor(type)
    return editorScheme?.getOrElse(type) {
      colorIds?.getOrDefault(type, defaultValue) ?: defaultValue
    } ?: defaultValue
  }

  override fun isDark(): Boolean {
    return this.isDarkScheme
  }
}

/**
 * Color scheme for a language.
 *
 * @property files The file types for this language color scheme.
 * @property styles The highlight styles.
 * @author Akash Yadav
 */
class LanguageScheme {

  internal val files = mutableListOf<String>()
  internal val styles = mutableMapOf<String, StyleDef>()
  internal val localScopes = TreeSet<String>()
  internal val localMembersScopes = TreeSet<String>()
  internal val localDefs = TreeSet<String>()
  internal val localDefVals = TreeSet<String>()
  internal val localRefs = TreeSet<String>()

  fun getFileTypes(): List<String> = files

  fun getStyles(): Map<String, StyleDef> = styles

  fun isLocalScope(capture: String): Boolean {
    return localScopes.contains(capture)
  }

  fun isMembersScope(capture: String): Boolean {
    return localMembersScopes.contains(capture)
  }

  fun isLocalDef(capture: String): Boolean {
    return localDefs.contains(capture)
  }

  fun isLocalDefVal(capture: String): Boolean {
    return localDefVals.contains(capture)
  }

  fun isLocalRef(capture: String): Boolean {
    return localRefs.contains(capture)
  }
}

/**
 * A color scheme style definition.
 *
 * @property fg The foreground color.
 * @property bg The background color.
 * @property bold Whether the highlighted region should have bold text.
 * @property italic Whether the highlighted region should have italic text.
 * @property strikeThrough Whether the highlighted region should have strikethrough text.
 * @property completion Whether code completions can be performed in the highlighted region.
 * @property maybeHexColor Whether the node represented by this style can contain HEX color strings.
 *   If this value is `true`, the node's text will be parsed to check if it represents a valid HEX
 *   color. If it does, that color will be used as the node's background color. The foreground color
 *   of the node will be automatically selected based on the HEX color's brightness.
 */
data class StyleDef(
    var fg: Int = EditorColorScheme.TEXT_NORMAL,
    var bg: Int = 0,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var strikeThrough: Boolean = false,
    var completion: Boolean = true,
    var maybeHexColor: Boolean = false,
) {

  /**
   * Make the style for the style definition.
   *
   * @see TextStyle.makeStyle
   */
  fun makeStyle(): Long {
    return TextStyle.makeStyle(fg, bg, bold, italic, strikeThrough, !completion)
  }

  /**
   * Make the static style for this style definition. The background color ID in the returned style
   * is always [EditorColorScheme.STATIC_SPAN_BACKGROUND] and the foreground color ID is always
   * [EditorColorScheme.STATIC_SPAN_FOREGROUND].
   */
  fun makeStaticStyle(): Long {
    return TextStyle.makeStyle(
        EditorColorScheme.STATIC_SPAN_FOREGROUND,
        EditorColorScheme.STATIC_SPAN_BACKGROUND,
        bold,
        italic,
        strikeThrough,
        !completion,
    )
  }
}
