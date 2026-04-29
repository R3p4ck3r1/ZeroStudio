package com.itsaky.androidide.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs

/**
 * Draggable edge-snap bubble view.
 * - Supports free drag in parent bounds
 * - Snaps to left/right edge on release
 * - Remembers vertical position by center Y ratio
 */
class EdgeSnapBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialCardView(context, attrs, defStyleAttr) {

  var verticalCenterRatio: Float = 0.35f
    private set

  var dragTopBoundaryProvider: (() -> Float)? = null

  private var touchDownRawX = 0f
  private var touchDownRawY = 0f
  private var startX = 0f
  private var startY = 0f
  private var dragging = false

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val parentView = parent as? View ?: return super.onTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        touchDownRawX = event.rawX
        touchDownRawY = event.rawY
        startX = x
        startY = y
        dragging = false
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = event.rawX - touchDownRawX
        val dy = event.rawY - touchDownRawY
        if (!dragging && (abs(dx) > 8f || abs(dy) > 8f)) dragging = true
        if (dragging) {
          val minY = (dragTopBoundaryProvider?.invoke() ?: 0f).coerceAtLeast(0f)
          val maxY = (parentView.height - height).toFloat().coerceAtLeast(minY)
          x = (startX + dx).coerceIn(0f, (parentView.width - width).toFloat())
          y = (startY + dy).coerceIn(minY, maxY)
        }
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (!dragging) {
          performClick()
          return true
        }
        val snapToLeft = x + width / 2f < parentView.width / 2f
        val targetX = if (snapToLeft) 0f else (parentView.width - width).toFloat()
        val minY = (dragTopBoundaryProvider?.invoke() ?: 0f).coerceAtLeast(0f)
        val maxY = (parentView.height - height).toFloat().coerceAtLeast(minY)
        val safeY = y.coerceIn(minY, maxY)
        verticalCenterRatio = ((safeY + height / 2f) / parentView.height).coerceIn(0f, 1f)
        animate().x(targetX).y(safeY).setDuration(220L).start()
        return true
      }
    }
    return super.onTouchEvent(event)
  }

  fun restoreVerticalPosition() {
    val parentView = parent as? View ?: return
    val minY = (dragTopBoundaryProvider?.invoke() ?: 0f).coerceAtLeast(0f)
    val maxY = (parentView.height - height).toFloat().coerceAtLeast(minY)
    val targetCenterY = parentView.height * verticalCenterRatio
    y = (targetCenterY - height / 2f).coerceIn(minY, maxY)
  }
}
