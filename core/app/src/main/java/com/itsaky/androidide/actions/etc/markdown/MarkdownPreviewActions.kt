package com.itsaky.androidide.actions.etc.markdown

import android.content.Context
import com.itsaky.androidide.actions.ActionsRegistry

/**
 * Registry for Markdown preview related actions.
 *
 * This object registers the Markdown preview action with the ActionsRegistry,
 * similar to how EditorToolboxActions manages toolbox entries.
 *
 * Usage:
 * ```
 * // In your initialization code (e.g., Application.onCreate)
 * MarkdownPreviewActions.register(context)
 * ```
 *
 * @author ZeroStudio
 */
object MarkdownPreviewActions {

  /**
   * Registers all Markdown preview actions with the ActionsRegistry.
   *
   * @param context The application context
   * @param order The order priority for the action
   */
  fun register(context: Context, order: Int = 200) {
    // Register Markdown Preview action
    ActionsRegistry.getInstance().registerAction(
      MarkdownPreviewAction(context, order)
    )
  }

  /**
   * Unregisters all Markdown preview actions from the ActionsRegistry.
   */
  fun unregister() {
    ActionsRegistry.getInstance().unregisterAction(MarkdownPreviewAction.ID)
  }
}
