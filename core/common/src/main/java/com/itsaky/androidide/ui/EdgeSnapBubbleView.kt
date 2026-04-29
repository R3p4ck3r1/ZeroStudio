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

/** 小米侧边“圆头小山峰”风格吸附控件（黑色+箭头）。 */
class EdgeSnapBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

  enum class Side { LEFT, RIGHT }

  var side: Side = Side.LEFT
    private set

  var centerYRatio: Float = 0.4f
    private set

  var dragTopBoundaryProvider: (() -> Float)? = null

  private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B2D31") }
  private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = resources.displayMetrics.density * 3f
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }

  private var downRawY = 0f
  private var startY = 0f
  private var dragging = false

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val w = (56 * resources.displayMetrics.density).toInt()
    val h = (220 * resources.displayMetrics.density).toInt()
    setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val w = width.toFloat()
    val h = height.toFloat()
    val path = Path()
    if (side == Side.LEFT) {
      path.moveTo(0f, 0f)
      path.lineTo(w * 0.55f, 0f)
      path.cubicTo(w * 1.05f, h * 0.18f, w * 1.05f, h * 0.36f, w * 0.62f, h * 0.50f)
      path.cubicTo(w * 1.05f, h * 0.64f, w * 1.05f, h * 0.82f, w * 0.55f, h)
      path.lineTo(0f, h)
    } else {
      path.moveTo(w, 0f)
      path.lineTo(w * 0.45f, 0f)
      path.cubicTo(w * -0.05f, h * 0.18f, w * -0.05f, h * 0.36f, w * 0.38f, h * 0.50f)
      path.cubicTo(w * -0.05f, h * 0.64f, w * -0.05f, h * 0.82f, w * 0.45f, h)
      path.lineTo(w, h)
    }
    path.close()
    canvas.drawPath(path, bgPaint)

    val cx = if (side == Side.LEFT) w * 0.47f else w * 0.53f
    val cy = h * 0.50f
    val s = resources.displayMetrics.density * 10f
    val arrow = Path()
    if (side == Side.LEFT) {
      arrow.moveTo(cx + s * 0.3f, cy - s)
      arrow.lineTo(cx - s * 0.3f, cy)
      arrow.lineTo(cx + s * 0.3f, cy + s)
    } else {
      arrow.moveTo(cx - s * 0.3f, cy - s)
      arrow.lineTo(cx + s * 0.3f, cy)
      arrow.lineTo(cx - s * 0.3f, cy + s)
    }
    canvas.drawPath(arrow, arrowPaint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val parentView = parent as? View ?: return super.onTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downRawY = event.rawY
        startY = y
        dragging = false
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        val dy = event.rawY - downRawY
        if (!dragging && abs(dy) > 8f) dragging = true
        if (dragging) {
          val minY = (dragTopBoundaryProvider?.invoke() ?: 0f).coerceAtLeast(0f)
          val maxY = (parentView.height - height).toFloat().coerceAtLeast(minY)
          y = (startY + dy).coerceIn(minY, maxY)
          centerYRatio = ((y + height / 2f) / parentView.height).coerceIn(0f, 1f)
        }
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (!dragging) performClick()
        return true
      }
    }
    return super.onTouchEvent(event)
  }

  fun attachToSide(newSide: Side) {
    side = newSide
    val parentView = parent as? View ?: run { invalidate(); return }
    x = if (side == Side.LEFT) 0f else (parentView.width - width).toFloat()
    invalidate()
  }

  fun restorePosition() {
    val parentView = parent as? View ?: return
    val minY = (dragTopBoundaryProvider?.invoke() ?: 0f).coerceAtLeast(0f)
    val maxY = (parentView.height - height).toFloat().coerceAtLeast(minY)
    y = (parentView.height * centerYRatio - height / 2f).coerceIn(minY, maxY)
    x = if (side == Side.LEFT) 0f else (parentView.width - width).toFloat()
  }
}
