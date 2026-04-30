package com.itsaky.androidide.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 横向顶部吸附的 Snap Bubble。
 */
class EdgeSnapBubbleView : View {

  private var backViewHeight: Float = 0f
  private var backEdgeWidth: Float = 0f
  private var backMaxWidth: Float = 0f

  private var thresholdLeft: Float = 0f
  private var thresholdRight: Float = 0f

  private var currentY: Float = 0f
  private var downX: Float = 0f
  private var isEdge: Boolean = false
  private var deltaX: Float = 0f
  private var mWidth: Int = 0
  private var isOnlyLeftBack: Boolean = false
  var left: Boolean = false
  var right: Boolean = false
  var forwardX: Float = 0f
  var bufferX: Float = 0f

  private var backPaint: Paint? = null
  private var arrowPaint: Paint? = null
  private var backPath: Path? = null
  private var arrowPath: Path? = null

  private var onBackListener: OnBackListener? = null
  private var onDragListener: OnDragListener? = null
  private var showArrowUp: Boolean = true

  fun setOnBackListener(onBackListener: OnBackListener?) {
    this.onBackListener = onBackListener
  }

  fun setOnDragListener(onDragListener: OnDragListener?) {
    this.onDragListener = onDragListener
  }

  fun setOnlyLeftBack(onlyLeftBack: Boolean) {
    isOnlyLeftBack = onlyLeftBack
  }

  constructor(context: Context) : this(context, null)

  constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    val density = resources.displayMetrics.density
    backViewHeight = 24f * density
    backEdgeWidth = 12f * density
    backMaxWidth = 56f * density
    init()
  }

  private fun init() {
    backPath = Path()
    arrowPath = Path()

    backPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    backPaint!!.color = 0xAA000000.toInt()
    backPaint!!.style = Paint.Style.FILL_AND_STROKE

    arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    arrowPaint!!.color = Color.WHITE
    arrowPaint!!.style = Paint.Style.STROKE
    arrowPaint!!.strokeWidth = 6f
    arrowPaint!!.strokeCap = Paint.Cap.ROUND
    arrowPaint!!.strokeJoin = Paint.Join.ROUND
  }

  override fun onTouchEvent(ev: MotionEvent): Boolean {
    val currentX = ev.x
    currentY = ev.y
    when (ev.action) {
      MotionEvent.ACTION_DOWN -> {
        downX = ev.x
        forwardX = downX
        isEdge = true
        left = true
        right = false
      }

      MotionEvent.ACTION_MOVE -> {
        deltaX = currentX - downX
        val diff = forwardX - currentX
        if (diff > 0) {
          if (currentX < thresholdLeft && left) {
            deltaX = backMaxWidth
            deltaX -= (thresholdLeft - currentX) / 2
            bufferX = deltaX
            if (deltaX < 0) {
              deltaX = 0f
              bufferX = 0f
            }
          } else if (currentX > thresholdRight && right) {
            bufferX -= diff
            deltaX = bufferX
          }
        } else {
          if (currentX < thresholdLeft && left) {
            bufferX += abs(diff)
            deltaX = bufferX
          } else if (currentX > thresholdRight && right) {
            deltaX = -backMaxWidth
            deltaX += (currentX - thresholdRight) / 2
            bufferX = deltaX
            if (deltaX < -backMaxWidth) {
              deltaX = -backMaxWidth
              bufferX = -backMaxWidth
            }
          }
        }
        forwardX = currentX
        onDragListener?.onDrag((deltaX / backMaxWidth).coerceIn(-1f, 1f))
        if (isEdge) invalidate()
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (isEdge) {
          if (deltaX >= backMaxWidth && left) {
            back()
          } else if (abs(deltaX) >= backMaxWidth && right) {
            if (!isOnlyLeftBack) {
              back()
            }
          }
          if (abs(deltaX) < backMaxWidth * 0.2f) {
            performClick()
          }
          onDragListener?.onRelease((deltaX / backMaxWidth).coerceIn(-1f, 1f))
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

  private fun back() {
    // 仅回调业务逻辑，不再触发系统返回上一级。
    onBackListener?.onBack()
    performClick()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    mWidth = MeasureSpec.getSize(widthMeasureSpec) + 1
    thresholdLeft = (mWidth / 3).toFloat()
    thresholdRight = thresholdLeft * 2
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (deltaX > backMaxWidth && left) {
      deltaX = backMaxWidth
    } else if (deltaX < -backMaxWidth && right) {
      deltaX = -backMaxWidth
    }
    Log.e("onDraw", "$deltaX")

    backPath!!.reset()
    arrowPath!!.reset()

    val drawDelta = if (deltaX == 0f) backMaxWidth * 0.35f else abs(deltaX)
    val widthF = width.toFloat()
    val heightF = height.toFloat().coerceAtLeast(1f)

    val humpHeight = (drawDelta / backMaxWidth * (heightF * 0.7f)).coerceAtLeast(heightF * 0.35f)
    backPath!!.moveTo(0f, 0f)
    backPath!!.lineTo(widthF, 0f)
    backPath!!.lineTo(widthF, heightF)
    backPath!!.quadTo(widthF * 0.75f, heightF - humpHeight, widthF * 0.5f, heightF - humpHeight)
    backPath!!.quadTo(widthF * 0.25f, heightF - humpHeight, 0f, heightF)
    backPath!!.close()
    canvas.drawPath(backPath!!, backPaint!!)

    val centerX = widthF / 2f
    val centerY = heightF / 2f
    val arrowHalf = (heightF * 0.22f).coerceAtLeast(4f)
    if (showArrowUp) {
      arrowPath!!.moveTo(centerX - arrowHalf, centerY + arrowHalf * 0.35f)
      arrowPath!!.lineTo(centerX, centerY - arrowHalf)
      arrowPath!!.lineTo(centerX + arrowHalf, centerY + arrowHalf * 0.35f)
    } else {
      arrowPath!!.moveTo(centerX - arrowHalf, centerY - arrowHalf * 0.35f)
      arrowPath!!.lineTo(centerX, centerY + arrowHalf)
      arrowPath!!.lineTo(centerX + arrowHalf, centerY - arrowHalf * 0.35f)
    }
    canvas.drawPath(arrowPath!!, arrowPaint!!)

    alpha = (drawDelta / backMaxWidth).coerceIn(0.35f, 1f)
  }

  fun setBackViewHeight(backViewHeight: Float) {
    this.backViewHeight = backViewHeight
  }

  fun setBackEdgeWidth(backEdgeWidth: Float) {
    this.backEdgeWidth = backEdgeWidth
  }

  fun setBackMaxWidth(backMaxWidth: Float) {
    this.backMaxWidth = backMaxWidth
  }

  fun getBackEdgeWidth(): Float = backEdgeWidth

  fun getBackMaxWidth(): Float = backMaxWidth

  fun getBackViewHeight(): Float = backViewHeight

  fun attachToSide(newSide: Side) {
    restorePosition()
  }

  fun restorePosition() {
    // 固定在 header_container 顶部边缘（父容器顶部）
    x = 0f
    y = 0f
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  fun setArrowExpanded(expanded: Boolean) {
    showArrowUp = expanded
    invalidate()
  }

  enum class Side { LEFT, RIGHT }

  interface OnBackListener {
    fun onBack()
  }

  interface OnDragListener {
    fun onDrag(fraction: Float)
    fun onRelease(fraction: Float)
  }
}
