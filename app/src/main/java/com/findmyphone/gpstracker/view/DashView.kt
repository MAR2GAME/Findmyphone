package com.findmyphone.gpstracker.view

import android.R.attr.height
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.apply

class DashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#261A1A1A")
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val y = height / 2f
        canvas.drawLine(0f, y, width.toFloat(), y, paint)
    }
}