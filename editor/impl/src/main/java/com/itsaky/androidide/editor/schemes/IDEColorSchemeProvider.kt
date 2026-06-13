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

import android.content.Context
import androidx.annotation.WorkerThread
import com.itsaky.androidide.eventbus.events.editor.ColorSchemeInvalidatedEvent
import com.itsaky.androidide.preferences.internal.EditorPreferences
import com.itsaky.androidide.syntax.colorschemes.SchemeAndroidIDE
import com.itsaky.androidide.utils.Environment
import com.itsaky.androidide.utils.isSystemInDarkMode
import java.io.File
import java.io.FileFilter
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.slf4j.LoggerFactory

/** @author Akash Yadav */
object IDEColorSchemeProvider {

  private val log = LoggerFactory.getLogger(IDEColorSchemeProvider::class.java)
  private val schemesDir = File(Environment.ANDROIDIDE_UI, "editor/schemes")

  private val schemes = ConcurrentHashMap<String, IDEColorScheme>()

  private const val SCHEME_NAME = "scheme.name"
  private const val SCHEME_VERSION = "scheme.version"
  private const val SCHEME_IS_DARK = "scheme.isDark"
  private const val SCHEME_FILE = "scheme.file"

  private var isDefaultSchemeLoaded = false
  private var isCurrentSchemeLoaded = false

  /**
   * The default color scheme.
   *
   * This property must not be accessed from the main thread as we may need to perform I/O
   * operations if the scheme isn't loaded yet.
   */
  private var defaultScheme: IDEColorScheme? = null
    get() {
      return field
          ?: getColorScheme(EditorPreferences.DEFAULT_COLOR_SCHEME).also { scheme ->
            field = scheme
            isDefaultSchemeLoaded = scheme != null
          }
    }

  /**
   * The current color scheme.
   *
   * This property must not be accessed from the main thread as we may need to perform I/O
   * operations if the scheme isn't loaded yet.
   */
  private var currentScheme: IDEColorScheme? = null
    get() {
      return field
          ?: getColorScheme(EditorPreferences.colorScheme).also { scheme ->
            field = scheme
            isCurrentSchemeLoaded = scheme != null
          }
    }

  /**
   * Get the color scheme with the given [name]. This reads the color schemes from file system if
   * the scheme isn't loaded yet.
   */
  @WorkerThread
  private fun getColorScheme(name: String): IDEColorScheme? {
    val scheme =
        schemes[name]
            ?: run {
              val fallbackScheme =
                  when {
                    name != EditorPreferences.DEFAULT_COLOR_SCHEME ->
                        schemes[EditorPreferences.DEFAULT_COLOR_SCHEME]
                    else -> schemes.values.firstOrNull { !it.key.endsWith("-dark") }
                  }

              if (fallbackScheme != null) {
                log.warn(
                    "Color scheme '{}' not found in loaded schemes, falling back to '{}'",
                    name,
                    fallbackScheme.key,
                )
              } else {
                log.error("Color scheme '{}' not found in loaded schemes", name)
              }

              fallbackScheme ?: return null
            }
    return loadColorScheme(scheme)
  }

  /** Loads the given [color scheme][scheme], then returns the same [scheme]. */
  private fun loadColorScheme(scheme: IDEColorScheme): IDEColorScheme? {
    return try {
      scheme.load()
      scheme.darkVariant?.load()
      scheme
    } catch (err: Exception) {
      log.error(
          "An error occurred while loading color scheme '{}' from file '{}'",
          scheme.name,
          scheme.file.absolutePath,
          err,
      )
      null
    }
  }

  /**
   * Initialize the color schemes. This lists the available color schemes (by reading the
   * `scheme.prop` file), but does not load them.
   */
  @JvmStatic
  @WorkerThread
  fun init() {
    val schemeDirs =
        schemesDir.listFiles(FileFilter { it.isDirectory && File(it, "scheme.prop").exists() })
            ?: run {
              log.error("No color schemes found in directory: {}", schemesDir.absolutePath)
              return
            }

    log.debug("Found {} scheme directories in {}", schemeDirs.size, schemesDir.absolutePath)

    for (schemeDir in schemeDirs) {
      val prop = File(schemeDir, "scheme.prop")
      val props =
          try {
            prop.reader().use { reader -> Properties().apply { load(reader) } }
          } catch (err: Exception) {
            log.error(
                "Failed to read properties for scheme '{}' at '{}'",
                schemeDir.name,
                prop.absolutePath,
                err,
            )
            continue
          }

      val name = props.getProperty(SCHEME_NAME, "Unknown")
      val version = props.getProperty(SCHEME_VERSION, "0").toInt()
      val isDark = props.getProperty(SCHEME_IS_DARK, "false").toBoolean()
      val file =
          props.getProperty(SCHEME_FILE)
              ?: run {
                log.error(
                    "Scheme '{}' does not specify 'scheme.file' in scheme.prop file at '{}'",
                    schemeDir.name,
                    prop.absolutePath,
                )
                ""
              }

      if (version <= 0) {
        log.warn("Version code of color scheme '{}' must be set to >= 1", schemeDir)
      }

      if (file.isBlank()) {
        continue
      }

      val schemeFile = File(schemeDir, file)
      if (!schemeFile.exists()) {
        log.error(
            "Scheme file '{}' does not exist for scheme '{}'",
            schemeFile.absolutePath,
            schemeDir.name,
        )
        continue
      }

      log.debug(
          "Registered color scheme: name='{}', key='{}', file='{}'",
          name,
          schemeDir.name,
          schemeFile.absolutePath,
      )

      val scheme = IDEColorScheme(schemeFile, schemeDir.name)
      scheme.name = name
      scheme.version = version
      scheme.isDarkScheme = isDark
      schemes[schemeDir.name] = scheme
    }

    schemes.values.forEach { it.darkVariant = schemes["${it.key}-dark"] }

    log.info("Initialized {} color schemes", schemes.size)
  }

  /**
   * Initializes the color schemes if the no color schemes are available.
   *
   * @see init
   */
  @JvmStatic
  fun initIfNeeded() {
    if (this.schemes.isEmpty()) {
      init()
    }
  }

  /**
   * Reads the current color scheme asynchronously from file system if it is not already loaded,
   * then invokes the [callback].
   *
   * @param context Context used to determine whether the system is in dark mode.
   * @param coroutineScope The scope used to read the scheme asynchronously.
   * @param callback The callback to receive the [SchemeAndroidIDE] instance.
   * @see readScheme
   */
  @JvmOverloads
  fun readSchemeAsync(
      context: Context,
      coroutineScope: CoroutineScope,
      type: String? = null,
      callbackContext: CoroutineContext = Dispatchers.Main.immediate,
      callback: (SchemeAndroidIDE?) -> Unit,
  ) {

    val loadedScheme =
        if (
            isCurrentSchemeLoaded &&
                (type == null || currentScheme?.getLanguageScheme(type) != null)
        ) {
          currentScheme
        } else if (isDefaultSchemeLoaded) {
          defaultScheme
        } else {
          null
        }

    if (loadedScheme != null) {
      coroutineScope.launch(callbackContext) { callback(readScheme(context, type)) }
      return
    }

    // scheme has not been loaded
    // load the scheme using the IO dispatcher
    coroutineScope.launch(Dispatchers.IO) {
      val scheme = readScheme(context, type)
      withContext(callbackContext) { callback(scheme) }
    }
  }

  /**
   * Reads the current color scheme synchronously.
   *
   * @see readSchemeAsync
   */
  @JvmOverloads
  @WorkerThread
  fun readScheme(context: Context, type: String? = null): SchemeAndroidIDE? {
    val scheme = getColorSchemeForType(type)
    if (scheme == null) {
      log.error("Failed to read color scheme for type '{}'", type)
      return null
    }

    val dark = scheme.darkVariant
    if (context.isSystemInDarkMode() && dark != null) {
      return dark
    }

    return scheme
  }

  /**
   * Get the color scheme for the given [file type][type]. If the current color scheme does not
   * support the given file type, the [default color scheme][defaultScheme] is returned.
   */
  @WorkerThread
  fun getColorSchemeForType(type: String?): IDEColorScheme? {
    if (type == null) {
      return currentScheme
    }

    val current = currentScheme
    if (current != null && current.getLanguageScheme(type) != null) {
      return current
    }

    if (current != null) {
      log.warn(
          "Color scheme '{}' does not support '{}'. Available languages: {}",
          current.name,
          type,
          current.languages.keys.joinToString(", "),
      )
      log.warn("Falling back to default color scheme")
    }

    val default = defaultScheme
    if (default != null && default.getLanguageScheme(type) != null) {
      return default
    }

    if (default != null) {
      log.error(
          "Default color scheme '{}' does not support '{}'. Available languages: {}",
          default.name,
          type,
          default.languages.keys.joinToString(", "),
      )
    }

    return default
  }

  fun list(): List<IDEColorScheme> {
    // filter out schemes that are dark variants of other schemes
    // schemes with both light and dark variant will be used according to system's dark mode
    return this.schemes.values.filter { !it.key.endsWith("-dark") }.toList()
  }

  /** Destroy the loaded color schemes. */
  fun destroy() {
    // Reset every scheme so that the accumulated state (colorIds, editorScheme, languages,
    // definitions, colorId counter) is released. We also null out darkVariant so that the
    // otherwise-reachable sibling scheme references don't keep the schemes alive in memory.
    this.schemes.values.forEach { scheme ->
      scheme.darkVariant = null
      scheme.reset()
    }
    this.schemes.clear()
    this.currentScheme = null
    this.isCurrentSchemeLoaded = false

    this.defaultScheme = null
    this.isDefaultSchemeLoaded = false
  }

  @WorkerThread
  fun reload() {
    destroy()
    init()
    EventBus.getDefault().post(ColorSchemeInvalidatedEvent())
  }
}
