package com.itsaky.androidide.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/** 固定在边缘的“圆头小山峰”手势气泡（黑色+箭头）。 */
class EdgeSnapBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

  enum class Side { LEFT, RIGHT }

  var side: Side = Side.LEFT
    private set

  private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2B2D31") }
  private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = resources.displayMetrics.density * 3f
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val w = (42 * resources.displayMetrics.density).toInt()
    val h = (256 * resources.displayMetrics.density).toInt()
    setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val w = width.toFloat()
    val h = height.toFloat()
    val path = Path()
    if (side == Side.LEFT) {
      path.moveTo(0f, 0f)
      path.lineTo(w * 0.26f, 0f)
      path.cubicTo(w * 0.98f, h * 0.22f, w * 0.98f, h * 0.40f, w * 0.32f, h * 0.52f)
      path.cubicTo(w * 0.98f, h * 0.64f, w * 0.98f, h * 0.82f, w * 0.26f, h)
      path.lineTo(0f, h)
    } else {
      path.moveTo(w, 0f)
      path.lineTo(w * 0.74f, 0f)
      path.cubicTo(w * 0.02f, h * 0.22f, w * 0.02f, h * 0.40f, w * 0.68f, h * 0.52f)
      path.cubicTo(w * 0.02f, h * 0.64f, w * 0.02f, h * 0.82f, w * 0.74f, h)
      path.lineTo(w, h)
    }
    path.close()
    canvas.drawPath(path, bgPaint)

    val cx = if (side == Side.LEFT) w * 0.55f else w * 0.45f
    val cy = h * 0.50f
    val s = resources.displayMetrics.density * 8f
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
