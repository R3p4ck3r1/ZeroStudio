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

/**
 * Kotlin 版 SlideBackView（保留原始手势拉拽动画与贝塞尔曲线公式）。
 *
 * 注意：本项目中该控件固定锚定在 page_switch_container 顶部边缘，
 * 不使用“屏幕左右边缘隐藏/触发”机制，因此相关逻辑默认始终可交互显示。
 */
class EdgeSnapBubbleView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

  enum class Side { LEFT, RIGHT }

  private val density = resources.displayMetrics.density

  /** backView 高度 */
  private var backViewHeight = 256f * density

  /** 边缘触发距离（保留字段，当前固定显示场景不依赖） */
  private var backEdgeWidth = 16f * density

  /** 最大返回触发距离 */
  private var backMaxWidth = 42f * density

  private var thresholdLeft = 0f
  private var thresholdRight = 0f

  private var currentY = 0f
  private var downX = 0f
  private var isEdge = false
  private var deltaX = 0f
  private var widthSize = 0
  private var isOnlyLeftBack = false
  private var left = false
  private var right = false
  private var forwardX = 0f
  private var bufferX = 0f

  private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xAA000000.toInt()
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

  var side: Side = Side.LEFT
    private set

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val currentX = event.x
    currentY = event.y
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downX = event.x
        forwardX = downX
        // 原 SlideBack 是“仅边缘触发”；此处按需求改为固定显示后可直接触发。
        isEdge = true
        left = side == Side.LEFT
        right = side == Side.RIGHT
      }
      MotionEvent.ACTION_MOVE -> {
        deltaX = if (left) currentX - downX else downX - currentX
        val diff = forwardX - currentX
        if (diff > 0) {
          if (currentX < thresholdLeft && left) {
            deltaX = backMaxWidth
            deltaX -= (thresholdLeft - currentX) / 2f
            bufferX = deltaX
            if (deltaX < 0) {
              deltaX = 0f
              bufferX = 0f
            }
          } else if ((currentX > thresholdRight) && right) {
            bufferX -= diff
            deltaX = bufferX
          }
        } else {
          if ((currentX < thresholdLeft) && left) {
            bufferX += abs(diff)
            deltaX = bufferX
          } else if (currentX > thresholdRight && right) {
            deltaX = backMaxWidth
            deltaX -= (currentX - thresholdRight) / 2f
            bufferX = deltaX
            if (deltaX < 0f) {
              deltaX = 0f
              bufferX = 0f
            }
          }
        }
        forwardX = currentX
        if (isEdge) invalidate()
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (isEdge) {
          if ((deltaX >= backMaxWidth && left) || (deltaX >= backMaxWidth && right && !isOnlyLeftBack)) {
            performClick()
          }
          deltaX = 0f
          invalidate()
        }
        left = false
        right = false
        isEdge = false
        bufferX = 0f
      }
    }
    return isEdge
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    widthSize = MeasureSpec.getSize(widthMeasureSpec) + 1
    thresholdLeft = widthSize / 3f
    thresholdRight = thresholdLeft * 2f
    val resolvedHeight = resolveSize(backViewHeight.toInt(), heightMeasureSpec)
    setMeasuredDimension(resolveSize(backMaxWidth.toInt() + 1, widthMeasureSpec), resolvedHeight)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val idleDelta = backMaxWidth * 0.38f
    val drawDeltaX = if (deltaX > 0f) deltaX.coerceAtMost(backMaxWidth) else idleDelta
    val deltaY = currentY - backViewHeight / 2f
    backPath.reset()
    arrowPath.reset()
    if (left || (!left && !right && side == Side.LEFT)) {
      backPath.moveTo(0f, deltaY)
      backPath.quadTo(0f, backViewHeight / 4f + deltaY, drawDeltaX / 3f, backViewHeight * 3f / 8f + deltaY)
      backPath.quadTo(
        drawDeltaX * 5f / 8f,
        backViewHeight / 2f + deltaY,
        drawDeltaX / 3f,
        backViewHeight * 5f / 8f + deltaY,
      )
      backPath.quadTo(0f, backViewHeight * 6f / 8f + deltaY, 0f, backViewHeight + deltaY)
      canvas.drawPath(backPath, backPaint)

      arrowPath.moveTo(drawDeltaX / 6f + (15f * (drawDeltaX / (widthSize / 6f))), backViewHeight * 15f / 32f + deltaY)
      arrowPath.lineTo(drawDeltaX / 6f, backViewHeight * 16.1f / 32f + deltaY)
      arrowPath.moveTo(drawDeltaX / 6f, backViewHeight * 15.9f / 32f + deltaY)
      arrowPath.lineTo(drawDeltaX / 6f + (15f * (drawDeltaX / (widthSize / 6f))), backViewHeight * 17f / 32f + deltaY)
      canvas.drawPath(arrowPath, arrowPaint)
    } else {
      val w = width.toFloat().coerceAtLeast(backMaxWidth + 1f)
      backPath.moveTo(w, deltaY)
      backPath.quadTo(w, backViewHeight / 4f + deltaY, w - drawDeltaX / 3f, backViewHeight * 3f / 8f + deltaY)
      backPath.quadTo(
        w - drawDeltaX * 5f / 8f,
        backViewHeight / 2f + deltaY,
        w - drawDeltaX / 3f,
        backViewHeight * 5f / 8f + deltaY,
      )
      backPath.quadTo(w, backViewHeight * 6f / 8f + deltaY, w, backViewHeight + deltaY)
      canvas.drawPath(backPath, backPaint)

      arrowPath.moveTo(w - drawDeltaX / 6f - (15f * (drawDeltaX / (widthSize / 6f))), backViewHeight * 15f / 32f + deltaY)
      arrowPath.lineTo(w - drawDeltaX / 6f, backViewHeight * 16.1f / 32f + deltaY)
      arrowPath.moveTo(w - drawDeltaX / 6f, backViewHeight * 15.9f / 32f + deltaY)
      arrowPath.lineTo(w - drawDeltaX / 6f - (15f * (drawDeltaX / (widthSize / 6f))), backViewHeight * 17f / 32f + deltaY)
      canvas.drawPath(arrowPath, arrowPaint)
    }
    alpha = (drawDeltaX / backMaxWidth).coerceIn(0.38f, 1f)
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
