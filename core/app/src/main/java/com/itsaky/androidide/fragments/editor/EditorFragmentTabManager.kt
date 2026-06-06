package com.itsaky.androidide.fragments.editor

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.tabs.TabLayout
import com.itsaky.androidide.R
import com.itsaky.androidide.databinding.ActivityEditorBinding
import java.io.File
import java.util.UUID

/**
 * Manager for handling fragment tabs in EditorHandlerActivity.
 *
 * This class manages the lifecycle of fragment tabs, including:
 * - Adding new tabs
 * - Switching between tabs
 * - Closing tabs
 * - Handling fragment arguments
 *
 * @author ZeroStudio
 */
class EditorFragmentTabManager(
  private val activity: FragmentActivity,
  private val binding: ActivityEditorBinding,
  private val containerId: Int
) {

  /**
   * Data class representing an open fragment tab.
   */
  data class OpenTab(
    val id: String,
    val entry: FragmentTabEntry,
    val fragment: Fragment,
    val arguments: Bundle?,
    val filePath: String?
  )

  private val openTabs = mutableMapOf<String, OpenTab>()

  /**
   * Opens a fragment tab with the given entry and optional file path.
   *
   * @param entry The FragmentTabEntry to open
   * @param filePath Optional file path to associate with this tab
   * @param args Optional arguments to pass to the fragment
   * @return The id of the opened tab
   */
  fun openTab(
    entry: FragmentTabEntry,
    filePath: String? = null,
    args: Bundle? = null
  ): String {
    val tabId = generateTabId(entry, filePath)

    // If tab already open, just switch to it
    if (openTabs.containsKey(tabId)) {
      switchToTab(tabId)
      return tabId
    }

    // Create new fragment instance
    val fragment = entry.createFragment()

    // Prepare arguments
    val fragmentArgs = args ?: Bundle()
    if (filePath != null) {
      fragmentArgs.putString(ARG_FILE_PATH, filePath)
    }
    fragment.arguments = fragmentArgs

    // Create open tab
    val openTab = OpenTab(
      id = tabId,
      entry = entry,
      fragment = fragment,
      arguments = fragmentArgs,
      filePath = filePath
    )

    openTabs[tabId] = openTab

    // Add tab to TabLayout
    addTabToLayout(entry, filePath, tabId)

    // Add fragment to container
    addFragmentToContainer(fragment, tabId)

    // Switch to the new tab
    switchToTab(tabId)

    return tabId
  }

  /**
   * Opens a fragment tab for a file with the given path.
   *
   * @param filePath The path of the file to open
   * @param fileExtension The file extension (e.g., "md", "txt")
   * @param args Optional arguments to pass to the fragment
   * @return The id of the opened tab, or null if no matching entry found
   */
  fun openFileTab(
    filePath: String,
    fileExtension: String,
    args: Bundle? = null
  ): String? {
    val entries = FragmentTabRegistry.getByFileExtension(fileExtension)
    if (entries.isEmpty()) {
      return null
    }

    // Use the first matching entry (highest priority due to ordering)
    val entry = entries.first()
    return openTab(entry, filePath, args)
  }

  /**
   * Closes a tab by its id.
   *
   * @param tabId The id of the tab to close
   * @return true if the tab was found and closed, false otherwise
   */
  fun closeTab(tabId: String): Boolean {
    val openTab = openTabs.remove(tabId) ?: return false

    // Remove from TabLayout
    val tabIndex = findTabIndex(tabId)
    if (tabIndex >= 0) {
      binding.tabs.removeTabAt(tabIndex)
    }

    // Remove fragment from container
    activity.supportFragmentManager.beginTransaction()
      .remove(openTab.fragment)
      .commitNow()

    return true
  }

  /**
   * Switches to a tab by its id.
   *
   * @param tabId The id of the tab to switch to
   * @return true if the tab was found and switched to, false otherwise
   */
  fun switchToTab(tabId: String): Boolean {
    val openTab = openTabs[tabId] ?: return false

    // Update tab selection in TabLayout
    val tabIndex = findTabIndex(tabId)
    if (tabIndex >= 0) {
      binding.tabs.selectTab(binding.tabs.getTabAt(tabIndex))
    }

    // Show the fragment, hide others
    showFragment(openTab.fragment)

    return true
  }

  /**
   * Gets the currently open tab id.
   *
   * @return The id of the current tab, or null if no tabs are open
   */
  fun getCurrentTabId(): String? {
    val selectedTab = binding.tabs.selectedTab
    return selectedTab?.tag as? String
  }

  /**
   * Gets all open tabs.
   *
   * @return List of all open tabs
   */
  fun getOpenTabs(): List<OpenTab> {
    return openTabs.values.toList()
  }

  /**
   * Checks if a tab is open for the given file path.
   *
   * @param filePath The file path to check
   * @return true if a tab is open for this file, false otherwise
   */
  fun isTabOpen(filePath: String): Boolean {
    return openTabs.values.any { it.filePath == filePath }
  }

  /**
   * Gets an open tab by its id.
   *
   * @param tabId The id of the tab to retrieve
   * @return The OpenTab if found, null otherwise
   */
  fun getTab(tabId: String): OpenTab? {
    return openTabs[tabId]
  }

  /**
   * Updates the title of a tab.
   *
   * @param tabId The id of the tab to update
   * @param newTitle The new title to set
   */
  fun updateTabTitle(tabId: String, newTitle: String) {
    openTabs[tabId]?.let { openTab ->
      val tabIndex = findTabIndex(tabId)
      if (tabIndex >= 0) {
        binding.tabs.getTabAt(tabIndex)?.text = newTitle
      }
    }
  }

  private fun generateTabId(entry: FragmentTabEntry, filePath: String?): String {
    return if (filePath != null) {
      "${entry.id}:$filePath"
    } else {
      "${entry.id}:${UUID.randomUUID()}"
    }
  }

  private fun addTabToLayout(entry: FragmentTabEntry, filePath: String?, tabId: String) {
    val tab = binding.tabs.newTab()
    tab.tag = tabId
    tab.text = getTabTitle(entry, filePath)
    tab.setIcon(entry.iconRes)
    binding.tabs.addTab(tab)
  }

  private fun getTabTitle(entry: FragmentTabEntry, filePath: String?): String {
    return if (filePath != null) {
      File(filePath).name
    } else {
      entry.title
    }
  }

  private fun addFragmentToContainer(fragment: Fragment, tabId: String) {
    activity.supportFragmentManager.beginTransaction()
      .add(containerId, fragment, tabId)
      .hide(fragment)
      .commitNow()
  }

  private fun showFragment(fragment: Fragment) {
    activity.supportFragmentManager.beginTransaction()
      .show(fragment)
      .commitNow()

    // Hide all other fragments
    openTabs.values.forEach { openTab ->
      if (openTab.fragment != fragment) {
        activity.supportFragmentManager.beginTransaction()
          .hide(openTab.fragment)
          .commitNow()
      }
    }
  }

  private fun findTabIndex(tabId: String): Int {
    for (i in 0 until binding.tabs.tabCount) {
      if (binding.tabs.getTabAt(i)?.tag == tabId) {
        return i
      }
    }
    return -1
  }

  /**
   * Cleans up all open tabs and releases resources.
   */
  fun closeAllTabs() {
    openTabs.keys.toList().forEach { closeTab(it) }
  }

  companion object {
    const val ARG_FILE_PATH = "file_path"
  }
}
