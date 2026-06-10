package com.itsaky.androidide.fragments.editor

import androidx.fragment.app.Fragment

/**
 * Registered editor tab contribution backed by a Fragment lifecycle rather than a CodeEditorView.
 */
data class FragmentTabEntry(
  val id: String,
  val title: String,
  val iconRes: Int,
  val fragmentClass: Class<out Fragment>,
  val fileExtension: String? = null,
  val fileExtensions: Set<String> = fileExtension?.let { setOf(it) } ?: emptySet(),
  val order: Int = 0,
  val fragmentFactory: (() -> Fragment)? = null,
) {
  fun createFragment(): Fragment = fragmentFactory?.invoke() ?: fragmentClass.getDeclaredConstructor().newInstance()

  fun matchesExtension(extension: String): Boolean {
    return fileExtensions.any { it.equals(extension, ignoreCase = true) }
  }
}
