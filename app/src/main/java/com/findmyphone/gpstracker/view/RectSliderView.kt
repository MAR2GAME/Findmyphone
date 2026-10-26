package com.findmyphone.gpstracker.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.findmyphone.gpstracker.R

class RectSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ==================== 数值范围 ====================
    var minValue: Float = 40f
        set(value) {
            if (field != value) {
                field = value
                syncProgressFromValue()
                invalidate()
            }
        }

    var maxValue: Float = 500f
        set(value) {
            if (field != value) {
                field = value
                syncProgressFromValue()
                invalidate()
            }
        }

    var currentValue: Float = minValue
        set(value) {
            val newValue = value.coerceIn(minValue, maxValue)
            if (field != newValue) {
                field = newValue
                currentProgress = (newValue - minValue) / (maxValue - minValue)
                invalidate()
                notifyProgressChanged(fromUser = false)
            }
        }

    var currentProgress: Float = 0f
        set(value) {
            val newProgress = value.coerceIn(0f, 1f)
            if (field != newProgress) {
                field = newProgress
                currentValue = minValue + newProgress * (maxValue - minValue)
                updateThumbPosition()
                invalidate()
                notifyProgressChanged(fromUser = false)
            }
        }

    // ==================== 监听器 ====================
    interface OnProgressChangeListener {
        fun onProgressChanged(view: RectSliderView, progress: Float, value: Float, fromUser: Boolean)
    }

    private var progressListener: OnProgressChangeListener? = null

    fun setOnProgressChangeListener(listener: OnProgressChangeListener?) {
        this.progressListener = listener
    }

    private fun notifyProgressChanged(fromUser: Boolean) {
        progressListener?.onProgressChanged(this, currentProgress, currentValue, fromUser)
    }

    interface OnSlideStopListener {
        fun onSlideStop(view: RectSliderView, finalValue: Float, finalProgress: Float)
    }

    private var slideStopListener: OnSlideStopListener? = null

    fun setOnSlideStopListener(listener: OnSlideStopListener?) {
        this.slideStopListener = listener
    }

    // ==================== 绘制相关 ====================
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1D4294F5")
        style = Paint.Style.FILL
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4294F5")
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4294F5")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp2px(12f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val trackHeight: Float = dp2px(18f)
    private val thumbWidth: Float = dp2px(5f)
    private val thumbHeight: Float = dp2px(30f)
    private val textMargin: Float = dp2px(4f)
    private var cornerRadius = 0f

    private var trackRect = RectF()
    private var thumbRect = RectF()
    private var thumbMinX = 0f
    private var thumbMaxX = 0f

    private var displayText = ""
    private var textBounds = Rect()
    private var textX = 0f
    private var textY = 0f

    // ==================== 触摸优化参数 ====================
    // 触摸热区扩展值（dp），用户在此范围内点击即视为拖拽滑块，不会跳转
    private val touchSlop = dp2px(12f)

    init {
        cornerRadius = trackHeight / 2
        isClickable = true
        isFocusable = true
        syncProgressFromValue()
        attrs?.let { parseAttributes(it) }
    }

    private fun parseAttributes(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.RectSliderView)
        try {
            minValue = typedArray.getFloat(R.styleable.RectSliderView_minValue, 40f)
            maxValue = typedArray.getFloat(R.styleable.RectSliderView_maxValue, 500f)
            currentProgress = typedArray.getFloat(R.styleable.RectSliderView_progress, 0f)
            typedArray.getColor(R.styleable.RectSliderView_trackColor, Color.parseColor("#1D4294F5"))
                .let { trackPaint.color = it }
            typedArray.getColor(R.styleable.RectSliderView_progressColor, Color.parseColor("#4294F5"))
                .let { progressPaint.color = it }
            typedArray.getColor(R.styleable.RectSliderView_thumbColor, Color.parseColor("#4294F5"))
                .let { thumbPaint.color = it }
            typedArray.getColor(R.styleable.RectSliderView_textColor, Color.WHITE)
                .let { textPaint.color = it }
            textPaint.textSize = typedArray.getDimension(R.styleable.RectSliderView_textSize, sp2px(12f))
        } finally {
            typedArray.recycle()
        }
        syncProgressFromValue()
    }

    private fun syncProgressFromValue() {
        currentProgress = (currentValue - minValue) / (maxValue - minValue)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val textHeight = measureTextHeight()
        val desiredHeight = (paddingTop + paddingBottom + textHeight + textMargin + thumbHeight).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    private fun measureTextHeight(): Float {
        textPaint.getTextBounds("500", 0, 3, textBounds)
        return textBounds.height().toFloat()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculatePositions()
    }

    private fun calculatePositions() {
        val textHeight = measureTextHeight()
        val thumbTopBase = paddingTop + textHeight + textMargin
        val thumbBottom = thumbTopBase + thumbHeight
        val thumbCenterY = (thumbTopBase + thumbBottom) / 2f
        val trackTop = thumbCenterY - trackHeight / 2
        val trackBottom = thumbCenterY + trackHeight / 2
        trackRect.set(paddingLeft.toFloat(), trackTop, (width - paddingRight).toFloat(), trackBottom)
        thumbMinX = trackRect.left
        thumbMaxX = trackRect.right - thumbWidth
        updateThumbPosition()
    }

    private fun updateThumbPosition() {
        val thumbLeft = thumbMinX + currentProgress * (thumbMaxX - thumbMinX)
        val thumbTop = trackRect.centerY() - thumbHeight / 2
        thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbWidth, thumbTop + thumbHeight)
        updateTextPosition()
    }

    private fun updateTextPosition() {
        if (width <= 0 || height <= 0) return

        displayText = "${currentValue.toInt()}"
        textPaint.getTextBounds(displayText, 0, displayText.length, textBounds)
        val textHalfWidth = textBounds.width() / 2f
        val textHeight = textBounds.height()

        val desiredTextX = thumbRect.centerX()
        val desiredTextY = thumbRect.top - textMargin

        val minTextX = paddingLeft + textHalfWidth + dp2px(4f)
        val maxTextX = width - paddingRight - textHalfWidth - dp2px(4f)

        textX = if (minTextX < maxTextX) {
            desiredTextX.coerceIn(minTextX, maxTextX)
        } else {
            (minTextX + maxTextX) / 2f
        }

        textY = desiredTextY
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        if (currentProgress > 0) {
            val progressRect = RectF(trackRect.left, trackRect.top, thumbRect.left, trackRect.bottom)
            val path = Path().apply {
                val radii = floatArrayOf(
                    cornerRadius, cornerRadius,  // 左上角
                    0f, 0f,                      // 右上角
                    0f, 0f,                      // 右下角
                    cornerRadius, cornerRadius   // 左下角
                )
                addRoundRect(progressRect, radii, Path.Direction.CW)
            }
            canvas.drawPath(path, progressPaint)
        }

        canvas.drawRoundRect(thumbRect, thumbWidth / 2, thumbWidth / 2, thumbPaint)
        canvas.drawText(displayText, textX, textY, textPaint)
    }

    // ==================== 触摸交互（优化版）====================
    private var isDragging = false
    private var dragStartProgress = 0f
    private var dragStartX = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)

                val x = event.x
                val y = event.y

                // 判断是否落在滑块的“热区”内
                if (isInThumbHotspot(x, y)) {
                    // 热区内：进入拖拽模式，不跳转进度，记录起始状态
                    isDragging = true
                    dragStartProgress = currentProgress
                    dragStartX = x
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } else if (trackRect.contains(x, y)) {
                    // 轨道上但远离滑块：直接跳转到点击位置
                    isDragging = true
                    val newProgress = progressFromX(x)
                    if (newProgress != currentProgress) {
                        currentProgress = newProgress
                        notifyProgressChanged(fromUser = true)
                    }
                    // 同步拖拽基准，避免后续 move 偏移错误
                    dragStartProgress = currentProgress
                    dragStartX = x
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } else {
                    // 完全在控件外，不处理
                    return false
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                parent?.requestDisallowInterceptTouchEvent(true)

                val moveX = event.x
                // 根据手指水平移动距离计算进度变化量
                val dx = moveX - dragStartX
                val range = thumbMaxX - thumbMinX
                if (range > 0) {
                    val deltaProgress = dx / range
                    val newProgress = (dragStartProgress + deltaProgress).coerceIn(0f, 1f)
                    if (newProgress != currentProgress) {
                        currentProgress = newProgress
                        notifyProgressChanged(fromUser = true)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                slideStopListener?.onSlideStop(this, currentValue, currentProgress)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 检查 (x, y) 是否在滑块的有效热区内（比视觉矩形大 touchSlop）
     */
    private fun isInThumbHotspot(x: Float, y: Float): Boolean {
        val hotRect = RectF(
            thumbRect.left - touchSlop,
            thumbRect.top - touchSlop,
            thumbRect.right + touchSlop,
            thumbRect.bottom + touchSlop
        )
        return hotRect.contains(x, y)
    }

    /**
     * 根据轨道 x 坐标计算进度（不依赖滑块）
     */
    private fun progressFromX(x: Float): Float {
        return ((x - trackRect.left) / trackRect.width()).coerceIn(0f, 1f)
    }

    // ==================== 保留旧方法（已废弃）====================
    @Deprecated("保留以供兼容，但已不再使用", ReplaceWith("progressFromX"))
    private fun getProgressFromTouch(touchX: Float, touchY: Float): Float {
        // 旧逻辑，已被新触摸处理替代
        return progressFromX(touchX)
    }

    // ==================== 公开方法 ====================
    fun setProgressAnimated(progress: Float, duration: Long = 200L) {
        val target = progress.coerceIn(0f, 1f)
        if (currentProgress == target) return
        ValueAnimator.ofFloat(currentProgress, target).apply {
            this.duration = duration
            addUpdateListener { animator ->
                currentProgress = animator.animatedValue as Float
            }
            start()
        }
    }

    fun setValueAnimated(value: Float, duration: Long = 200L) {
        val targetValue = value.coerceIn(minValue, maxValue)
        setProgressAnimated((targetValue - minValue) / (maxValue - minValue), duration)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.5f
    }

    private fun dp2px(dp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    private fun sp2px(sp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
}