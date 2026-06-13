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

package com.itsaky.androidide.editor.ui

import android.view.View
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.base.EditorPopupWindow
import org.slf4j.LoggerFactory

/**
 * Abstract class for all [IDEEditor] popup windows.
 *
 * @author Akash Yadav
 */
abstract class AbstractPopupWindow(editor: CodeEditor, features: Int) :
    EditorPopupWindow(editor, features) {

  companion object {

    private val log = LoggerFactory.getLogger(AbstractPopupWindow::class.java)
  }

  /**
   * Tracks whether we have already attached [detachListener] to the editor.
   * Used so that we don't register the same listener more than once when
   * [register]/[unregister] are called repeatedly.
   */
  private var detachListenerRegistered = false

  /**
   * Dismiss the popup as soon as the anchor view (the editor) is detached from
   * the window.
   *
   * Root-cause fix for
   * `java.lang.IllegalArgumentException: View=android.widget.PopupWindow$PopupDecorView{...} not attached to window manager`
   * thrown from `android.widget.PopupWindow$1.onViewAttachedToWindow` →
   * `PopupWindow.alignToAnchor` → `PopupWindow.update` → `WindowManagerGlobal.findViewLocked`.
   *
   * What happens without this fix: when the host activity is paused or destroyed
   * (configuration change, screen rotation, navigating away, low-memory kill, …)
   * the editor's [View] is detached. The framework's `PopupWindow` keeps an internal
   * `OnAttachStateChangeListener` on the anchor view; the next time that anchor is
   * re-attached (e.g. activity recreation) the listener fires `alignToAnchor()` which
   * calls `update()` on a popup whose decor view is no longer registered with
   * `WindowManagerGlobal` — and the framework throws `IllegalArgumentException`. The
   * try/catch inside the sora base class's `applyWindowAttributes` is **not** in this
   * code path, because the call originates from framework code during view traversal.
   *
   * The proper cure is to dismiss the popup *before* the anchor can be re-attached
   * to a stale popup. We do that by listening for the anchor being detached and
   * tearing the popup down immediately, so the framework has nothing to update.
   */
  private val detachListener =
      object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
          // Nothing to do on attach; the popup will be re-shown by its owner if needed.
        }

        override fun onViewDetachedFromWindow(v: View) {
          // Tear the popup down so that the framework's anchor listener has nothing
          // to align to on the next attach cycle. dismiss() is a no-op if not showing.
          try {
            dismiss()
          } catch (Throwable t) {
            log.warn("Failed to dismiss popup window '{}' on anchor detach", javaClass.name, t)
          }
        }
      }

  override fun show() {
    (editor as? IDEEditor)?.ensureWindowsDismissed()
    if (!editor.isAttachedToWindow) {
      log.error(
          "Trying to show popup window '{}' when editor is not attached to window",
          javaClass.name,
      )
      return
    }

    // Make sure the popup will be torn down the moment the editor leaves the window.
    // This is the root-cause guard against the framework's anchor re-attach crash.
    if (!detachListenerRegistered) {
      editor.addOnAttachStateChangeListener(detachListener)
      detachListenerRegistered = true
    }

    super.show()
  }

  override fun dismiss() {
    // Drop the listener only when we know we are going away. The popup can legitimately
    // be re-shown later (e.g. user types again), in which case `show()` will re-register.
    if (detachListenerRegistered) {
      try {
        editor.removeOnAttachStateChangeListener(detachListener)
      } catch (Throwable t) {
        log.warn("Failed to remove detach listener for popup window '{}'", javaClass.name, t)
      }
      detachListenerRegistered = false
    }
    super.dismiss()
  }

  override fun isShowing(): Boolean {
    @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    return popup?.isShowing ?: false
  }
}
