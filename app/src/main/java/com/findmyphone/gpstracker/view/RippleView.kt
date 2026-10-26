package com.findmyphone.gpstracker.view
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.apply
import kotlin.collections.forEach
import kotlin.math.max
import kotlin.math.min
import kotlin.ranges.coerceIn

class RippleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private var centerX = 0f
    private var centerY = 0f
    private var minRadius = 0f
    private var maxRadius = 0f

    private val ripples = mutableListOf<RippleItem>()
    private var maxRipples = 5  // 限制同时存在的波纹数量，避免性能问题

    fun setCenter(x: Float, y: Float) {
        centerX = x
        centerY = y
        invalidate()
    }

    fun setRadiusLimit(min: Float, max: Float) {
        minRadius = min
        maxRadius = max
    }

    fun addRipple(duration: Long = 1500L) {
        if (maxRadius <= minRadius + 1f) return  // 无效范围，不添加
        if (ripples.size >= maxRipples) return   // 限制数量

        val item = RippleItem(minRadius, maxRadius, duration)
        item.animator.addUpdateListener {
            item.currentRadius = it.animatedValue as Float
            val progress = (item.currentRadius - minRadius) / (maxRadius - minRadius)
            item.currentAlpha = (0.6f * (1f - progress)).coerceIn(0f, 0.6f)
            invalidate()
        }
        item.animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                ripples.remove(item)
                invalidate()
            }
        })
        item.animator.start()
        ripples.add(item)
    }

    fun clearAllRipples() {
        ripples.forEach { it.animator.cancel() }
        ripples.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (ripple in ripples) {
            paint.alpha = (ripple.currentAlpha * 255).toInt()
            canvas.drawCircle(centerX, centerY, ripple.currentRadius, paint)
        }
    }

    private inner class RippleItem(
        startRadius: Float,
        endRadius: Float,
        duration: Long
    ) {
        var currentRadius = startRadius
        var currentAlpha = 0.6f

        val animator = ValueAnimator.ofFloat(startRadius, endRadius).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
        }
    }
}