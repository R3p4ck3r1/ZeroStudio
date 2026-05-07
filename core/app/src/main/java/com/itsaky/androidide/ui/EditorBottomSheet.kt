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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.GravityInt
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
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
import org.slf4j.LoggerFactory

/**
 * Bottom sheet shown in editor activity.
 *
 * Refactored to support Unified Floating Bottom Bar, precise 3D toggle animations,
 * IME (Soft Keyboard) native syncing, and proportional slide transitions.
 *
 * @author Akash Yadav
 * @author android_zero (Unified Architecture, IME Sync & Animation Fixes)
 */
class EditorBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

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

  // 软键盘安全区域及状态管理
  private var currentBottomInset = 0
  private var isHeaderVisible = true
  
  // 占位符变量（在新的统一架构中，不再需要独立控制该模式，保留以防外部崩溃）
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
        return@setOnClickListener
      }
      (fragment as ShareableOutputFragment).clearOutput()
    }

    // 延迟初始化触摸气泡：等布局测量完成后恢复坐标，解决首次启动变形挤压 BUG
    binding.pageSwitchGestureBubble.post {
      setupPageSwitchGestureBubble()
      binding.pageSwitchGestureBubble.restorePosition()
      binding.pageSwitchGestureBubble.invalidate()
    }
  }

  /**
   * 核心重构：接管 IME 与 PeekHeight，消除双模态与撕裂 Bug。
   */
  private fun setupDynamicPeekHeightAndIME() {
    // 1. 监听浮动头部的高度变动，更新 BottomSheet 的露头高度 (PeekHeight)
    binding.floatingHeaderArea.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
      val newHeight = bottom - top
      if (newHeight > 0 && newHeight != (oldBottom - oldTop)) {
        updatePeekHeight()
      }
    }

    // 2. 原生软键盘同步：将 IME 映射为 BottomSheet 的 BottomPadding
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
      val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
      val navInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      val isImeVisibleNow = insets.isVisible(WindowInsetsCompat.Type.ime())

      val targetBottomPadding = if (isImeVisibleNow) imeInsets.bottom else navInsets.bottom

      if (currentBottomInset != targetBottomPadding) {
        currentBottomInset = targetBottomPadding
        view.updatePadding(bottom = targetBottomPadding)

        // 告知内部工具栏进行辅助处理
        binding.externalSymbolInputView.setImeBottomInset(if (isImeVisibleNow) imeInsets.bottom else 0)
        
        // 软键盘弹起导致总高度增加，需要即时同步 PeekHeight 使得 Toolbar 升起
        updatePeekHeight()
      }

      behavior.isGestureInsetBottomIgnored = true
      insets
    }
  }

  private fun updatePeekHeight() {
    behavior.peekHeight = binding.floatingHeaderArea.height + currentBottomInset
  }

  /**
   * 配置气泡手势：
   * - 点击时：立体 3D 动画显隐 Header
   * - 滑动时：上下拖拽抽屉
   */
  private fun setupPageSwitchGestureBubble() {
    binding.pageSwitchGestureBubble.setOrientation(com.itsaky.androidide.ui.EdgeSnapBubbleView.Orientation.VERTICAL)
    binding.pageSwitchGestureBubble.setPosition(com.itsaky.androidide.ui.EdgeSnapBubbleView.Position.TOP)

    binding.pageSwitchGestureBubble.setOnBubbleClickListener {
      toggleHeaderVisibility()
    }

    binding.pageSwitchGestureBubble.setOnBubbleGestureListener(
        object : com.itsaky.androidide.ui.EdgeSnapBubbleView.OnBubbleGestureListener {
          override fun onDrag(fraction: Float) {
            // Fraction < 0 (向上滑), Fraction > 0 (向下滑)
            // 可选的额外视觉反馈
          }

          override fun onRelease(fraction: Float) {
            if (fraction < -0.15f) {
              // 向上拉 -> 展开 BottomSheet
              tryExpandSheetFromControl()
            } else if (fraction > 0.15f) {
              // 向下拉 -> 折叠 BottomSheet
              forceCollapse()
            }
          }
        }
    )
  }

  /**
   * 采用 3D 平移+透明度动画 隐藏/显示 Header 区域，实现“从 AdvancedSymbolInputView 背部抽插”的立体效果。
   */
  private fun toggleHeaderVisibility() {
    val transition = TransitionSet().apply {
      addTransition(Slide(Gravity.BOTTOM))
      addTransition(Fade())
      duration = 250
      interpolator = FastOutSlowInInterpolator()
      ordering = TransitionSet.ORDERING_TOGETHER
    }

    // 将动画挂载在 Overlay 容器上
    TransitionManager.beginDelayedTransition(binding.headerOverlayContainer, transition)

    isHeaderVisible = !isHeaderVisible
    val visibility = if (isHeaderVisible) View.VISIBLE else View.GONE

    // 控制 Header 组件，但保留 pageSwitchGestureBubble 让其永远可以被再次点击
    binding.headerContainer.visibility = visibility
    binding.cardView.visibility = visibility
    binding.border.root.visibility = visibility

    // 触发布局改变监听，重新修正 PeekHeight
    binding.floatingHeaderArea.post { updatePeekHeight() }
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
            // 解决上下平移时的同步百分比隐藏与显示
            val headerArea = binding.floatingHeaderArea
            val contentArea = binding.drawerContentArea
            
            // 将浮动头部根据展开进度向上移出屏幕外，并让抽屉内容补位
            val shift = headerArea.height * slideOffset
            headerArea.translationY = -shift
            contentArea.translationY = -shift
            
            // 按比例淡出（加快速率，使得滑动到半程即可完全透明）
            headerArea.alpha = (1f - slideOffset * 2f).coerceIn(0f, 1f)

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
  // =     DO NOT USE in new code! Kept to avoid crashes in old activities.  =
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
  fun refreshSymbolInput(editor: CodeEditorView) {}
}