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

package com.itsaky.androidide.ui

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.GravityInt
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.blankj.utilcode.util.ThreadUtils.runOnUiThread
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
import com.itsaky.androidide.R
import com.itsaky.androidide.adapters.DiagnosticsAdapter
import com.itsaky.androidide.adapters.EditorBottomSheetTabAdapter
import com.itsaky.androidide.adapters.SearchListAdapter
import com.itsaky.androidide.databinding.LayoutEditorBottomSheetBinding
import com.itsaky.androidide.fragments.output.ShareableOutputFragment
import com.itsaky.androidide.models.LogLine
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.tasks.TaskExecutor.CallbackWithError
import com.itsaky.androidide.tasks.TaskExecutor.executeAsync
import com.itsaky.androidide.tasks.TaskExecutor.executeAsyncProvideError
import com.itsaky.androidide.utils.IntentUtils.shareFile
import com.itsaky.androidide.utils.flashError
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.Callable
import kotlin.math.abs
import org.slf4j.LoggerFactory

/**
 * Bottom sheet shown in editor activity.
 *
 * @author Akash Yadav
 * @author android_zero
 */
class EditorBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

  @JvmField var binding: LayoutEditorBottomSheetBinding
  val pagerAdapter: EditorBottomSheetTabAdapter

  private val behavior: BottomSheetBehavior<EditorBottomSheet> by lazy {
    BottomSheetBehavior.from(this).apply {
      isFitToContents = false
      skipCollapsed = false
      isHideable = false
    }
  }

  private var suppressNextHeaderClickExpand = false
  private var headerExpandEnabled = true
  private var expandBlocked = false
  private var behaviorCallbackAttached = false

  var onHeaderPageChanged: ((Int) -> Unit)? = null
  var onActionTextChanged: ((CharSequence) -> Unit)? = null
  var onActionProgressChanged: ((Int) -> Unit)? = null
  var onStatusChanged: ((CharSequence, Int) -> Unit)? = null
  var onSlideAction: ((Float) -> Unit)? = null

  // 软键盘底部安全区域补丁
  private var currentBottomInset = 0

  var isExternalSymbolMode = false

  companion object {
    private val log = LoggerFactory.getLogger(EditorBottomSheet::class.java)

    const val CHILD_HEADER = 0
    const val CHILD_ACTION = 1
    const val STATE_EXTERNAL_SYMBOL = -1
  }

  init {
    if (context !is FragmentActivity) {
      throw IllegalArgumentException("EditorBottomSheet must be set up with a FragmentActivity")
    }

    orientation = VERTICAL
    val inflater = LayoutInflater.from(context)
    binding = LayoutEditorBottomSheetBinding.inflate(inflater, this, true)
    
    pagerAdapter = EditorBottomSheetTabAdapter(context)
    binding.pager.adapter = pagerAdapter

    initialize(context)
    setupDynamicPeekHeightAndIME()
  }

  private fun initialize(context: FragmentActivity) {
    val mediator =
        TabLayoutMediator(binding.tabs, binding.pager, true, true) { tab, position ->
          tab.text = pagerAdapter.getTitle(position)
        }

    mediator.attach()
    binding.pager.isUserInputEnabled = false
    binding.pager.offscreenPageLimit = pagerAdapter.itemCount - 1

    binding.tabs.addOnTabSelectedListener(
        object : OnTabSelectedListener {
          override fun onTabSelected(tab: Tab) {
            val fragment: Fragment = pagerAdapter.getFragmentAtIndex(tab.position)
            if (fragment is ShareableOutputFragment) {
              binding.clearFab.show()
              binding.shareOutputFab.show()
            } else {
              binding.clearFab.hide()
              binding.shareOutputFab.hide()
            }
          }

          override fun onTabUnselected(tab: Tab) {}
          override fun onTabReselected(tab: Tab) {}
        }
    )

    binding.shareOutputFab.setOnClickListener {
      val fragment = pagerAdapter.getFragmentAtIndex(binding.tabs.selectedTabPosition)

      if (fragment !is ShareableOutputFragment) {
        log.error("Unknown fragment: {}", fragment)
        return@setOnClickListener
      }

      val filename = fragment.getFilename()

      @Suppress("DEPRECATION")
      val progress =
          android.app.ProgressDialog.show(context, null, context.getString(string.please_wait))
      executeAsync(fragment::getContent) {
        progress.dismiss()
        shareText(it, filename)
      }
    }

    TooltipCompat.setTooltipText(binding.clearFab, context.getString(string.title_clear_output))
    binding.clearFab.setOnClickListener {
      val fragment: Fragment = pagerAdapter.getFragmentAtIndex(binding.tabs.selectedTabPosition)
      if (fragment !is ShareableOutputFragment) {
        log.error("Unknown fragment: {}", fragment)
        return@setOnClickListener
      }
      (fragment as ShareableOutputFragment).clearOutput()
    }

    // 延迟初始化触摸气泡以防止宽高校准错误挤压
    binding.pageSwitchGestureBubble.post {
      setupPageSwitchGestureBubble()
    }
  }

  /**
   * 处理软键盘 (IME) 同步和 PeekHeight 自动适配。
   * 让 CoordinatorLayout 与系统的 WindowInsets 联合接管所有位移交互。
   */
  private fun setupDynamicPeekHeightAndIME() {
    // 监听浮动头部的高度变动，更新 BottomSheet 的露头高度
    binding.floatingHeaderArea.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
        val newHeight = bottom - top
        if (newHeight > 0 && newHeight != (oldBottom - oldTop)) {
            updatePeekHeight()
        }
    }

    // 将 WindowInsets 拦截用于 IME 同步
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        val navInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val isImeVisibleNow = insets.isVisible(WindowInsetsCompat.Type.ime())

        val targetBottomPadding = if (isImeVisibleNow) imeInsets.bottom else navInsets.bottom

        if (currentBottomInset != targetBottomPadding) {
            currentBottomInset = targetBottomPadding
            view.updatePadding(bottom = targetBottomPadding)
            
            // 告知 AdvancedSymbolInputView 我们底部有了 Inset（让其内部处理可能需要的适配）
            binding.externalSymbolInputView.setImeBottomInset(if (isImeVisibleNow) imeInsets.bottom else 0)
            
            // 因为 BottomSheet 自己被垫高了，PeekHeight 需要加上这部分垫高的数值，才能确保 Header 留在视图上方
            updatePeekHeight()
        }

        // 让行为不被系统默认的 IME 手势拦截机制破坏
        behavior.isGestureInsetBottomIgnored = true
        insets
    }
  }

  private fun updatePeekHeight() {
      behavior.peekHeight = binding.floatingHeaderArea.height + currentBottomInset
  }

  private fun setupPageSwitchGestureBubble() {
      binding.pageSwitchGestureBubble.setOrientation(com.itsaky.androidide.ui.EdgeSnapBubbleView.Orientation.HORIZONTAL)
      binding.pageSwitchGestureBubble.setPosition(com.itsaky.androidide.ui.EdgeSnapBubbleView.Position.TOP)
      
      binding.pageSwitchGestureBubble.setOnBubbleClickListener {
          if (behavior.state == BottomSheetBehavior.STATE_EXPANDED) {
              forceCollapse()
          } else {
              tryExpandSheetFromControl()
          }
      }
      
      binding.pageSwitchGestureBubble.setOnBubbleGestureListener(
          object : com.itsaky.androidide.ui.EdgeSnapBubbleView.OnBubbleGestureListener {
              override fun onDrag(fraction: Float) {
                  val absFrac = abs(fraction)
                  val alpha = (1f - absFrac * 0.8f).coerceIn(0.2f, 1f)
                  binding.headerContainer.alpha = alpha
              }

              override fun onRelease(fraction: Float) {
                  if (fraction > 0.15f) {
                      tryExpandSheetFromControl()
                  } else if (fraction < -0.15f) {
                      forceCollapse()
                  }
                  binding.headerContainer.alpha = 1f
              }
          }
      )
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    ensureBehaviorCallbackAttached()
  }

  private fun ensureBehaviorCallbackAttached() {
    if (behaviorCallbackAttached) return
    behavior.addBottomSheetCallback(
        object : BottomSheetBehavior.BottomSheetCallback() {
          override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (!canExpandSheet() && newState == BottomSheetBehavior.STATE_EXPANDED) {
              forceCollapse()
            }
          }

          override fun onSlide(bottomSheet: View, slideOffset: Float) {
            onSlideAction?.invoke(slideOffset)
          }
        }
    )
    behaviorCallbackAttached = true
  }

  fun showChild(index: Int) {
    binding.headerContainer.displayedChild = index
    onHeaderPageChanged?.invoke(if (index == CHILD_ACTION) CHILD_ACTION else CHILD_HEADER)
  }

  fun suppressNextHeaderExpand() {
    suppressNextHeaderClickExpand = true
  }

  fun setBottomSheetDragEnabled(enabled: Boolean) {
    behavior.isDraggable = enabled
  }

  fun setExpandBlocked(blocked: Boolean) = setExpandAllowed(!blocked)

  fun setExpandAllowed(allowed: Boolean) {
    expandBlocked = !allowed
    behavior.isDraggable = allowed
    if (!allowed) {
      suppressNextHeaderExpand()
      forceCollapse()
    }
  }

  fun canExpandSheet(): Boolean {
    return !expandBlocked && headerExpandEnabled
  }

  fun tryExpandSheetFromControl(): Boolean {
    if (!canExpandSheet()) return false
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
    return true
  }

  fun forceCollapse() {
    if (behavior.state != BottomSheetBehavior.STATE_COLLAPSED) {
      behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }
  }

  fun suspendHeaderExpandFor(durationMs: Long) {
    headerExpandEnabled = false
    binding.root.removeCallbacks(resumeHeaderExpandRunnable)
    binding.root.postDelayed(resumeHeaderExpandRunnable, durationMs)
  }

  private val resumeHeaderExpandRunnable = Runnable { headerExpandEnabled = true }

  fun setActionText(text: CharSequence) {
    onActionTextChanged?.invoke(text)
    binding.bottomAction.actionText.text = text
  }

  fun setActionProgress(progress: Int) {
    onActionProgressChanged?.invoke(progress)
    binding.bottomAction.progress.setProgressCompat(progress, true)
  }

  fun appendApkLog(line: LogLine) {
    pagerAdapter.logFragment?.appendLog(line)
  }

  fun appendBuildOut(str: String?) {
    pagerAdapter.buildOutputFragment?.appendOutput(str)
  }

  fun clearBuildOutput() {
    pagerAdapter.buildOutputFragment?.clearOutput()
  }

  fun handleDiagnosticsResultVisibility(errorVisible: Boolean) {
    runOnUiThread { pagerAdapter.diagnosticsFragment?.isEmpty = errorVisible }
  }

  fun handleSearchResultVisibility(errorVisible: Boolean) {
    runOnUiThread { pagerAdapter.searchResultFragment?.isEmpty = errorVisible }
  }

  fun setDiagnosticsAdapter(adapter: DiagnosticsAdapter) {
    runOnUiThread { pagerAdapter.diagnosticsFragment?.setAdapter(adapter) }
  }

  fun setSearchResultAdapter(adapter: SearchListAdapter) {
    runOnUiThread { pagerAdapter.searchResultFragment?.setAdapter(adapter) }
  }

  fun setStatus(text: CharSequence, @GravityInt gravity: Int) {
    onStatusChanged?.invoke(text, gravity)
    binding.buildStatus.statusText.gravity = gravity
    binding.buildStatus.statusText.text = text
  }

  private fun shareFile(file: File) {
    shareFile(context, file, "text/plain")
  }

  @Suppress("DEPRECATION")
  private fun shareText(text: String?, type: String) {
    if (text == null || TextUtils.isEmpty(text)) {
      flashError(context.getString(string.msg_output_text_extraction_failed))
      return
    }
    val pd =
        android.app.ProgressDialog.show(
            context,
            null,
            context.getString(string.please_wait),
            true,
            false,
        )
    executeAsyncProvideError(
        Callable { writeTempFile(text, type) },
        CallbackWithError<File> { result: File?, error: Throwable? ->
          pd.dismiss()
          if (result == null || error != null) {
            log.warn("Unable to share output", error)
            return@CallbackWithError
          }
          shareFile(result)
        },
    )
  }

  private fun writeTempFile(text: String, type: String): File {
    val file: Path = context.filesDir.toPath().resolve("$type.txt")
    try {
      if (Files.exists(file)) {
        Files.delete(file)
      }
      Files.write(file, text.toByteArray(StandardCharsets.UTF_8), CREATE_NEW, WRITE)
    } catch (e: IOException) {
      log.error("Unable to write output to file", e)
    }
    return file.toFile()
  }

  // =========================================================================
  // =     DEPRECATED COMPATIBILITY STUBS FOR BaseEditorActivity             =
  // =     DO NOT USE in new code! To be removed in next refactor step.      =
  // =========================================================================

  @Deprecated("No longer needed in unified floating architecture")
  fun setOffsetAnchor(view: View, symbolInputPage: View) {}

  @Deprecated("No longer needed in unified floating architecture")
  fun resetSymbolInputPageHeight() {}
  
  @Deprecated("No longer needed in unified floating architecture")
  fun setImeVisible(isVisible: Boolean) {}

  @Deprecated("No longer needed in unified floating architecture")
  fun onSoftInputChanged() {}

  @Deprecated("No longer needed in unified floating architecture")
  fun onSlide(sheetOffset: Float) {
      onSlideAction?.invoke(sheetOffset)
  }

  @Deprecated("No longer needed in unified floating architecture")
  fun refreshSymbolInput(editor: CodeEditorView) {}
}