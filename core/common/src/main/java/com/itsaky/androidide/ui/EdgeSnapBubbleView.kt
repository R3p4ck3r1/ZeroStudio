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
 * 屏幕边缘滑动与返回上一级气泡手势控件。
 * 
 * 作者：android_zero
 */
class EdgeSnapBubbleView : View {

  /** backView 高度 */
  private var backViewHeight: Float = 0f

  /** 边缘触发距离 */
  private var backEdgeWidth: Float = 0f

  /** 最大返回触发距离 */
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

  private var onBubbleClickListener: OnBubbleClickListener? = null
  private var onBubbleGestureListener: OnBubbleGestureListener? = null
  private var side: Side = Side.LEFT
  private var position: Position = Position.LEFT
  private var orientation: Orientation = Orientation.VERTICAL
  private var isMirrored: Boolean = false
  private var showArrowUp: Boolean = true

  /** 绑定气泡区域点击事件 */
  fun setOnBubbleClickListener(listener: OnBubbleClickListener?) {
    onBubbleClickListener = listener
  }

  /** 绑定气泡上下/左右触摸拖拽的完整手势事件 */
  fun setOnBubbleGestureListener(listener: OnBubbleGestureListener?) {
    onBubbleGestureListener = listener
  }

  fun setPosition(newPosition: Position) {
    position = newPosition
    side = when (newPosition) {
      Position.LEFT, Position.TOP -> Side.LEFT
      Position.RIGHT, Position.BOTTOM -> Side.RIGHT
    }
    restorePosition()
  }

  fun setOrientation(newOrientation: Orientation) {
    orientation = newOrientation
  }

  fun setMirrored(mirrored: Boolean) {
    isMirrored = mirrored
  }

  fun setOnlyLeftBack(onlyLeftBack: Boolean) {
    isOnlyLeftBack = onlyLeftBack
  }

  constructor(context: Context) : this(context, null)

  constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    val density = resources.displayMetrics.density
    backViewHeight = 220f * density
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
    // 兼容上下拖拽手势。由于组件在 HORIZONTAL 模式下已被逻辑旋转，Y轴移动其实对应原生态X轴拉伸
    val currentTouchX = if (orientation == Orientation.HORIZONTAL) ev.y else ev.x
    // 固定绘制锚点，保障拖拽手势时视觉驼峰完美居中
    currentY = if (orientation == Orientation.HORIZONTAL) backViewHeight / 2f else ev.y
    
    when (ev.action) {
      MotionEvent.ACTION_DOWN -> {
        downX = currentTouchX
        forwardX = downX
        
        isEdge = true
        left = true
        right = false
      }

      MotionEvent.ACTION_MOVE -> {
        deltaX = downX - currentTouchX
        val diff = forwardX - currentTouchX
        if (diff > 0) {
          if (currentTouchX < thresholdLeft && left) {
            deltaX = backMaxWidth
            deltaX -= (thresholdLeft - currentTouchX) / 2
            bufferX = deltaX
            if (deltaX < 0) {
              deltaX = 0f
              bufferX = 0f
            }
          } else if (currentTouchX > thresholdRight && right) {
            bufferX -= diff
            deltaX = bufferX
          }
        } else {
          if (currentTouchX < thresholdLeft && left) {
            bufferX += abs(diff)
            deltaX = bufferX
          } else if (currentTouchX > thresholdRight && right) {
            deltaX = -backMaxWidth
            deltaX += (currentTouchX - thresholdRight) / 2
            bufferX = deltaX
            if (deltaX < -backMaxWidth) {
              deltaX = -backMaxWidth
              bufferX = -backMaxWidth
            }
          }
        }
        forwardX = currentTouchX
        if (isEdge) {
          onBubbleGestureListener?.onDrag(getDragFraction())
          invalidate()
        }
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (isEdge) {
          // 轻触作为点击事件响应
          if (abs(deltaX) < backMaxWidth * 0.2f) {
            performClick()
          }
          onBubbleGestureListener?.onRelease(getDragFraction())
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

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    var finalWidth = MeasureSpec.getSize(widthMeasureSpec)
    var finalHeight = MeasureSpec.getSize(heightMeasureSpec)

    // 水平模式本意是让宽度保证足够绘制空间（而不是抬高高度）
    if (orientation == Orientation.HORIZONTAL) {
        val minWidth = (backMaxWidth * 1.5f).toInt()
        if (finalWidth < minWidth) {
            finalWidth = minWidth
        }
    }

    setMeasuredDimension(finalWidth, finalHeight)
    mWidth = finalWidth + 1
    thresholdLeft = (mWidth / 3).toFloat()
    thresholdRight = thresholdLeft * 2
  }

  override fun onDraw(canvas: Canvas) {
    if (deltaX > backMaxWidth && left) {
      deltaX = backMaxWidth
    } else if (deltaX < -backMaxWidth && right) {
      deltaX = -backMaxWidth
    }
    val deltaY = currentY - backViewHeight / 2
    backPath!!.reset()
    arrowPath!!.reset()

    // 保留默认可见山峰，以引导用户进行拖拽
    val drawDelta = if (deltaX == 0f) backMaxWidth * 0.35f else abs(deltaX)

    canvas.save()

    if (orientation == Orientation.HORIZONTAL) {
        // 先移动到中点，容纳驼峰避免被顶边裁掉
        canvas.translate(width / 2f, backMaxWidth)
        // 逆时针旋转90度，让向右（正X）画出的曲线变为朝上凸起
        canvas.rotate(-90f)
        // 将绘制原点拉回中心对齐
        canvas.translate(0f, -backViewHeight / 2f)
    }

    if ((deltaX > 0 && left) || (deltaX == 0f && !right)) {
      backPath!!.moveTo(0f, deltaY)
      backPath!!.quadTo(0f, backViewHeight / 4 + deltaY, drawDelta / 3, backViewHeight * 3 / 8 + deltaY)
      backPath!!.quadTo(drawDelta * 5 / 8, backViewHeight / 2 + deltaY, drawDelta / 3, backViewHeight * 5 / 8 + deltaY)
      backPath!!.quadTo(0f, backViewHeight * 6 / 8 + deltaY, 0f, backViewHeight + deltaY)
      canvas.drawPath(backPath!!, backPaint!!)

      val midX = drawDelta / 6f
      val topY = backViewHeight * 15f / 32f + deltaY
      val centerY = backViewHeight * 16f / 32f + deltaY
      val bottomY = backViewHeight * 17f / 32f + deltaY
      val tipOffset = 15f * (drawDelta / (mWidth / 6f))
      if (showArrowUp) {
        arrowPath!!.moveTo(midX, centerY)
        arrowPath!!.lineTo(midX + tipOffset, topY)
        arrowPath!!.moveTo(midX, centerY)
        arrowPath!!.lineTo(midX + tipOffset, bottomY)
      } else {
        arrowPath!!.moveTo(midX + tipOffset, centerY)
        arrowPath!!.lineTo(midX, topY)
        arrowPath!!.moveTo(midX + tipOffset, centerY)
        arrowPath!!.lineTo(midX, bottomY)
      }
      canvas.drawPath(arrowPath!!, arrowPaint!!)
    } else if (deltaX < 0 && right) {
      if (!isOnlyLeftBack) {
        deltaX = abs(deltaX)
        backPath!!.moveTo(mWidth.toFloat(), deltaY)
        backPath!!.quadTo(mWidth.toFloat(), backViewHeight / 4 + deltaY, mWidth - deltaX / 3, backViewHeight * 3 / 8 + deltaY)
        backPath!!.quadTo(mWidth - deltaX * 5 / 8, backViewHeight / 2 + deltaY, mWidth - deltaX / 3, backViewHeight * 5 / 8 + deltaY)
        backPath!!.quadTo(mWidth.toFloat(), backViewHeight * 6 / 8 + deltaY, mWidth.toFloat(), backViewHeight + deltaY)
        canvas.drawPath(backPath!!, backPaint!!)

        arrowPath!!.moveTo(mWidth - deltaX / 6 - 15 * (deltaX / (mWidth / 6f)), backViewHeight * 15 / 32 + deltaY)
        arrowPath!!.lineTo(mWidth - deltaX / 6, backViewHeight * 16.1f / 32 + deltaY)
        arrowPath!!.moveTo(mWidth - deltaX / 6, backViewHeight * 15.9f / 32 + deltaY)
        arrowPath!!.lineTo(mWidth - deltaX / 6 - 15 * (deltaX / (mWidth / 6f)), backViewHeight * 17 / 32 + deltaY)
        canvas.drawPath(arrowPath!!, arrowPaint!!)
      }
    }
    
    canvas.restore()
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
    side = newSide
    position = if (newSide == Side.LEFT) Position.LEFT else Position.RIGHT
    restorePosition()
  }

  fun restorePosition() {
    val parentView = parent as? View ?: return
    when (position) {
      Position.LEFT -> {
        x = 0f
        y = ((parentView.height - height) / 2f).coerceAtLeast(0f)
      }
      Position.RIGHT -> {
        x = (parentView.width - width).toFloat()
        y = ((parentView.height - height) / 2f).coerceAtLeast(0f)
      }
      Position.TOP -> {
        x = ((parentView.width - width) / 2f).coerceAtLeast(0f)
        y = 0f
      }
      Position.BOTTOM -> {
        x = ((parentView.width - width) / 2f).coerceAtLeast(0f)
        y = (parentView.height - height).toFloat()
      }
    }
  }

  private fun getDragFraction(): Float {
    if (backMaxWidth <= 0f) return 0f
    return (deltaX / backMaxWidth).coerceIn(-1f, 1f)
  }

  override fun performClick(): Boolean {
    onBubbleClickListener?.onClick(this)
    super.performClick()
    return true
  }

  fun setArrowExpanded(expanded: Boolean) {
    showArrowUp = expanded
    invalidate()
  }

  enum class Side { LEFT, RIGHT }
  enum class Position { LEFT, RIGHT, TOP, BOTTOM }
  enum class Orientation { VERTICAL, HORIZONTAL }

  fun interface OnBubbleClickListener {
    fun onClick(view: EdgeSnapBubbleView)
  }

  interface OnBubbleGestureListener {
    /** 拖拽实时回调：返回值为 [-1.0, 1.0] 拉伸进度的百分比。负数代表拉向内层，正数拉向外层。 */
    fun onDrag(fraction: Float)
    /** 拖拽放开回调：判断手势距离以执行对应动作。 */
    fun onRelease(fraction: Float)
  }
}
