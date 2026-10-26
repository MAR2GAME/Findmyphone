package com.findmyphone.gpstracker.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import com.findmyphone.gpstracker.R

import kotlin.apply
import kotlin.let
import kotlin.ranges.coerceIn


class RotatingRingView
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_STROKE_WIDTH = 20f
        private const val DEFAULT_RING_COLOR = "#337EFF"
        private const val DEFAULT_BACKGROUND_COLOR = "#33337EFF"
        private const val DEFAULT_ANIMATION_DURATION = 800L
        private const val DEFAULT_ARC_LENGTH = 180f
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val rectF = RectF()
    private var startAngle = 0f
    private var rotationAnimator: ValueAnimator? = null

    // 缓存计算值
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    // 可配置属性
    private var ringStrokeWidth = DEFAULT_STROKE_WIDTH
    private var ringColor = Color.parseColor(DEFAULT_RING_COLOR)
    private var backgroundColor = Color.parseColor(DEFAULT_BACKGROUND_COLOR)
    private var animationDuration = DEFAULT_ANIMATION_DURATION
    private var arcLength = DEFAULT_ARC_LENGTH

    init {

        initAttributes(attrs)
    }

    private fun setupPaints() {
        ringPaint.apply {
            strokeWidth = ringStrokeWidth
            color = ringColor
        }

        backgroundPaint.apply {
            strokeWidth = ringStrokeWidth
            color = backgroundColor
        }
    }

    private fun initAttributes(attrs: AttributeSet?) {
        if (attrs == null) return

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.RotatingRingView)
        try {
            ringStrokeWidth = typedArray.getDimension(
                R.styleable.RotatingRingView_ringWidth,
                DEFAULT_STROKE_WIDTH
            )
            ringColor = typedArray.getColor(
                R.styleable.RotatingRingView_ringColor,
                ringColor
            )
            backgroundColor = typedArray.getColor(
                R.styleable.RotatingRingView_backgroundColor,
                backgroundColor
            )

            // 添加新的自定义属性
            animationDuration = typedArray.getInt(
                R.styleable.RotatingRingView_animationDuration,
                DEFAULT_ANIMATION_DURATION.toInt()
            ).toLong()

            arcLength = typedArray.getFloat(
                R.styleable.RotatingRingView_arcLength,
                DEFAULT_ARC_LENGTH
            ).coerceIn(0f, 360f)
        } finally {
            typedArray.recycle()
        }

        setupPaints()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateDimensions(w, h)
    }

    private fun updateDimensions(width: Int, height: Int) {
        centerX = width / 2f
        centerY = height / 2f
        radius = kotlin.comparisons.minOf(centerX, centerY) - ringStrokeWidth / 2f

        rectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
    }

    override fun onDraw(canvas: Canvas) {
        // 绘制背景圆环
        canvas.drawArc(rectF, 0f, 360f, false, backgroundPaint)
        // 绘制旋转圆环
        canvas.drawArc(rectF, startAngle, arcLength, false, ringPaint)
    }

    fun startAnimation() {
        if (rotationAnimator?.isRunning == true) return

        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
           duration = animationDuration
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                startAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        rotationAnimator?.let {
            it.cancel()
            it.removeAllUpdateListeners()
        }
        rotationAnimator = null
    }

    fun setRingColor(@ColorInt color: Int) {
        ringColor = color
        ringPaint.color = color
        invalidate()
    }

    fun setMyBackgroundColor(@ColorInt color: Int) {
        backgroundColor = color
        backgroundPaint.color = color
        invalidate()
    }

    fun setRingWidth(width: Float) {
        ringStrokeWidth = width
        ringPaint.strokeWidth = width
        backgroundPaint.strokeWidth = width
        invalidate()
    }

    fun setAnimationDuration(duration: Long) {
        animationDuration = duration
        restartAnimationIfNeeded()
    }

    fun setArcLength(length: Float) {
        arcLength = length.coerceIn(0f, 360f)
        invalidate()
    }

    private fun restartAnimationIfNeeded() {
        if (rotationAnimator?.isRunning == true) {
            stopAnimation()
            startAnimation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isVisible) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        when (visibility) {
            VISIBLE -> startAnimation()
            else -> stopAnimation()
        }
    }
}