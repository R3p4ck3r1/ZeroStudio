package com.itsaky.androidide.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 顶部横向 Snap Bubble（用于切换/收起，不再承担系统返回行为）。
 */
class EdgeSnapBubbleView : View {

  /** backView 高度 */
  private var backViewHeight: Float = 0f

  /** 边缘触发距离 */
  private var backEdgeWidth: Float = 0f

  /** 最大返回触发距离 */
  private var backMaxWidth: Float = 0f

  private var downY: Float = 0f
  private var deltaY: Float = 0f
  private var tracking = false
  private var triggerDistance: Float = 0f

  private var backPaint: Paint? = null
  private var arrowPaint: Paint? = null
  private var backPath: Path? = null
  private var arrowPath: Path? = null

  private var onBackListener: OnBackListener? = null
  private var showArrowUp: Boolean = true

  fun setOnBackListener(onBackListener: OnBackListener?) {
    this.onBackListener = onBackListener
  }

  constructor(context: Context) : this(context, null)

  constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    // 原始源码通过 styleable/dimen 读取。当前模块未定义对应 attrs，改为等价默认值。
    val density = resources.displayMetrics.density
    backViewHeight = 24f * density
    backEdgeWidth = 12f * density
    backMaxWidth = 20f * density
    triggerDistance = 14f * density
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
    when (ev.action) {
      MotionEvent.ACTION_DOWN -> {
        downY = ev.y
        deltaY = 0f
        tracking = true
        parent.requestDisallowInterceptTouchEvent(true)
        invalidate()
      }

      MotionEvent.ACTION_MOVE -> {
        if (!tracking) return false
        deltaY = (ev.y - downY).coerceIn(-backMaxWidth, backMaxWidth)
        invalidate()
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (tracking) {
          val draggedEnough = kotlin.math.abs(deltaY) >= triggerDistance
          if (draggedEnough || ev.action == MotionEvent.ACTION_UP) {
            performClick()
          }
          deltaY = 0f
          tracking = false
          parent.requestDisallowInterceptTouchEvent(false)
          invalidate()
        }
      }
    }
    return true
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    backPath!!.reset()
    arrowPath!!.reset()

    val w = width.toFloat()
    val h = height.toFloat().coerceAtLeast(1f)
    val bump = (backMaxWidth * 0.5f + kotlin.math.abs(deltaY) * 0.6f).coerceAtMost(backMaxWidth)

    backPath!!.moveTo(0f, 0f)
    backPath!!.lineTo(w, 0f)
    backPath!!.lineTo(w, h)
    backPath!!.quadTo(w * 0.75f, h - bump, w * 0.5f, h - bump)
    backPath!!.quadTo(w * 0.25f, h - bump, 0f, h)
    backPath!!.close()
    canvas.drawPath(backPath!!, backPaint!!)

    val cx = w / 2f
    val cy = h / 2f + bump * 0.15f
    val arrow = (h * 0.22f).coerceAtLeast(4f)
    if (showArrowUp) {
      arrowPath!!.moveTo(cx - arrow, cy + arrow * 0.25f)
      arrowPath!!.lineTo(cx, cy - arrow)
      arrowPath!!.lineTo(cx + arrow, cy + arrow * 0.25f)
    } else {
      arrowPath!!.moveTo(cx - arrow, cy - arrow * 0.25f)
      arrowPath!!.lineTo(cx, cy + arrow)
      arrowPath!!.lineTo(cx + arrow, cy - arrow * 0.25f)
    }
    canvas.drawPath(arrowPath!!, arrowPaint!!)
    alpha = 1f
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
    x = 0f
    y = 0f
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  fun setArrowExpanded(expanded: Boolean) {
    // expanded=true 表示容器显示中，箭头朝上（点击后收起）
    showArrowUp = expanded
    invalidate()
  }

  enum class Side { LEFT, RIGHT }

  interface OnBackListener {
    fun onBack()
  }
}
