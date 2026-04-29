package com.itsaky.androidide.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 复刻 SlideBack 的贝塞尔曲线边缘气泡效果。 */
class EdgeSnapBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

  enum class Side { LEFT, RIGHT }

  var side: Side = Side.LEFT
    private set

  private val density = resources.displayMetrics.density
  private val backViewHeight = 256f * density
  private val backMaxWidth = 42f * density

  private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#AA000000")
    style = Paint.Style.FILL_AND_STROKE
  }
  private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 3f * density
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val backPath = Path()
  private val arrowPath = Path()

  private var downX = 0f
  private var forwardX = 0f
  private var currentY = backViewHeight / 2f
  private var deltaX = 0f
  private var bufferX = 0f
  private var dragging = false

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val w = (backMaxWidth + 1f).toInt()
    val h = backViewHeight.toInt()
    setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val currentX = event.x
    currentY = event.y.coerceIn(0f, height.toFloat())
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downX = currentX
        forwardX = downX
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        val diff = forwardX - currentX
        deltaX = if (side == Side.LEFT) currentX - downX else downX - currentX
        if (diff > 0) {
          bufferX -= abs(diff) * 0.1f
        } else {
          bufferX += abs(diff) * 0.1f
        }
        deltaX = (deltaX + bufferX).coerceIn(0f, backMaxWidth)
        forwardX = currentX
        invalidate()
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        val shouldTrigger = dragging && deltaX >= backMaxWidth * 0.92f
        dragging = false
        deltaX = 0f
        bufferX = 0f
        invalidate()
        if (shouldTrigger) performClick()
        return true
      }
    }
    return super.onTouchEvent(event)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val w = width.toFloat().coerceAtLeast(backMaxWidth + 1f)
    val h = backViewHeight
    val idleReveal = backMaxWidth * 0.36f
    val reveal = if (dragging) min(backMaxWidth, max(0f, deltaX)) else idleReveal
    val deltaY = (currentY - h / 2f).coerceIn(-h / 2f, height - h / 2f)
    backPath.reset()
    arrowPath.reset()

    if (side == Side.LEFT) {
      backPath.moveTo(0f, deltaY)
      backPath.quadTo(0f, h / 4f + deltaY, reveal / 3f, h * 3f / 8f + deltaY)
      backPath.quadTo(reveal * 5f / 8f, h / 2f + deltaY, reveal / 3f, h * 5f / 8f + deltaY)
      backPath.quadTo(0f, h * 6f / 8f + deltaY, 0f, h + deltaY)
      canvas.drawPath(backPath, bgPaint)

      arrowPath.moveTo(reveal / 6f + (15f * (reveal / (w / 6f))), h * 15f / 32f + deltaY)
      arrowPath.lineTo(reveal / 6f, h * 16.1f / 32f + deltaY)
      arrowPath.moveTo(reveal / 6f, h * 15.9f / 32f + deltaY)
      arrowPath.lineTo(reveal / 6f + (15f * (reveal / (w / 6f))), h * 17f / 32f + deltaY)
    } else {
      backPath.moveTo(w, deltaY)
      backPath.quadTo(w, h / 4f + deltaY, w - reveal / 3f, h * 3f / 8f + deltaY)
      backPath.quadTo(w - reveal * 5f / 8f, h / 2f + deltaY, w - reveal / 3f, h * 5f / 8f + deltaY)
      backPath.quadTo(w, h * 6f / 8f + deltaY, w, h + deltaY)
      canvas.drawPath(backPath, bgPaint)

      arrowPath.moveTo(w - reveal / 6f - (15f * (reveal / (w / 6f))), h * 15f / 32f + deltaY)
      arrowPath.lineTo(w - reveal / 6f, h * 16.1f / 32f + deltaY)
      arrowPath.moveTo(w - reveal / 6f, h * 15.9f / 32f + deltaY)
      arrowPath.lineTo(w - reveal / 6f - (15f * (reveal / (w / 6f))), h * 17f / 32f + deltaY)
    }
    canvas.drawPath(arrowPath, arrowPaint)
    alpha = 1f
  }

  fun attachToSide(newSide: Side) {
    side = newSide
    val parentView = parent as? View ?: run { invalidate(); return }
    x = if (side == Side.LEFT) 0f else (parentView.width - width).toFloat()
    invalidate()
  }

  fun restorePosition() {
    val parentView = parent as? View ?: return
    x = if (side == Side.LEFT) 0f else (parentView.width - width).toFloat()
  }
}
