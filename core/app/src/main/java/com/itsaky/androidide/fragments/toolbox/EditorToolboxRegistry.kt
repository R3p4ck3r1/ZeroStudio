package com.itsaky.androidide.fragments.toolbox

import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registry for editor toolbox entries.
 *
 * Entries are deliberately lightweight descriptors so tools can be plugged in or removed without
 * instantiating their Fragment until the user opens that tool tab.
 */
object EditorToolboxRegistry {
  private val entries = CopyOnWriteArrayList<EditorToolboxEntry>()

  fun register(entry: EditorToolboxEntry) {
    entries.removeAll { it.id == entry.id }
    entries += entry
  }

  fun unregister(id: String) {
    entries.removeAll { it.id == id }
  }

  fun getEntries(): List<EditorToolboxEntry> = entries.sortedWith(compareBy<EditorToolboxEntry> { it.order }.thenBy { it.title })

  fun find(id: String): EditorToolboxEntry? = entries.firstOrNull { it.id == id }
}

data class EditorToolboxEntry(
  val id: String,
  val title: String,
  val description: String,
  @DrawableRes val iconRes: Int,
  val fragmentClass: Class<out Fragment>,
  val order: Int,
)
