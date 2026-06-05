package com.itsaky.androidide.fragments.editor

import android.view.View
import androidx.fragment.app.Fragment

/**
 * Data class representing a fragment tab entry for the EditorHandlerActivity tab layout.
 *
 * @property id Unique identifier for this tab entry
 * @property title The display title for the tab (uses filename if file-based, otherwise fixed title)
 * @property iconRes Icon resource ID for the tab
 * @property fragmentClass The class of the Fragment to instantiate
 * @property fileExtension Optional file extension to bind this tab to (e.g., "md", "mdr")
 * @property order Display order for the tab
 * @author ZeroStudio
 */
data class FragmentTabEntry(
  val id: String,
  val title: String,
  val iconRes: Int,
  val fragmentClass: Class<out Fragment>,
  val fileExtension: String? = null,
  val order: Int = 0,
) {

  /**
   * Creates a new instance of the fragment.
   * @return A new instance of the fragmentClass
   */
  fun createFragment(): Fragment {
    return fragmentClass.newInstance()
  }

  /**
   * Checks if this tab entry matches the given file extension.
   * @param extension The file extension to check (without the dot)
   * @return true if the extension matches, false otherwise
   */
  fun matchesExtension(extension: String): Boolean {
    return fileExtension?.equals(extension, ignoreCase = true) == true
  }
}
