package com.findmyphone.gpstracker.view

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.ProgressDialog.show
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.databinding.DataBindingUtil.setContentView
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.command.dp2px

class RateUsDialog(context: Context) : Dialog(context) {

    private var onClickListener: OnRateClickListener? = null
    private lateinit var starViews: List<ImageView>
    private var currentRating = 0                      // 0..5
    private var starBoundaries = mutableListOf<Pair<Int, Int>>() // 缓存左右边界(px)
    private var starLayout: LinearLayout? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    init {
        initializeDialog()
    }

    private fun initializeDialog() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_rate, null).also {
            setContentView(it)
            initializeViews(it)
        }
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        configureWindowProperties()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeViews(view: View) {
        // 获取星星 ImageView
        starViews = listOf(
            view.findViewById(R.id.star1),
            view.findViewById(R.id.star2),
            view.findViewById(R.id.star3),
            view.findViewById(R.id.star4),
            view.findViewById(R.id.star5)
        )
        updateStars(currentRating)

        starLayout = view.findViewById(R.id.starLayout)

        // 等待布局完成，缓存每颗星的左右边界
        starLayout?.let { layout ->
            globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                starBoundaries.clear()
                for (star in starViews) {
                    starBoundaries.add(star.left to star.right)
                }
                layout.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            }
            layout.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        }

        // 设置滑动触摸监听
        starLayout?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    starLayout?.parent?.requestDisallowInterceptTouchEvent(true)
                    val newRating = getRatingForTouchPosition(event.x)
                    if (newRating != currentRating) {
                        currentRating = newRating
                        updateStars(currentRating)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    starLayout?.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        // 提交按钮
        val btSubmit = view.findViewById<android.widget.Button>(R.id.bt_submit)
        btSubmit.setOnClickListener {
            onClickListener?.onRateClicked(currentRating)
        }
    }

    /**
     * 根据触摸点 X 坐标计算应点亮几颗星
     * @param touchX 相对于 starLayout 的 X 坐标
     */
    private fun getRatingForTouchPosition(touchX: Float): Int {
        // 优先使用缓存边界
        if (starBoundaries.isNotEmpty()) {
            for (i in starBoundaries.indices) {
                if (touchX < starBoundaries[i].first) return i
            }
            return starBoundaries.size
        }

        // 降级方案：实时获取 left（要求布局已完成）
        if (starViews.isNotEmpty() && starViews[0].width > 0) {
            for (i in starViews.indices) {
                if (touchX < starViews[i].left) return i
            }
            return starViews.size
        }

        // 完全未就绪时返回当前评分，不更新
        return currentRating
    }

    /**
     * 刷新星星图片
     * 注意：请确保 res/mipmap/star_filled.png 和 star_empty.png 存在
     */
    private fun updateStars(rating: Int) {
        for (i in starViews.indices) {
            val resId = if (i < rating) R.mipmap.star_filled else R.mipmap.star_empty
            starViews[i].setImageResource(resId)
        }
    }

    private fun configureWindowProperties() {
        window?.let {
            val params = it.attributes
            params.width =dp2px(context, 320)
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            it.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            it.attributes = params
            it.setGravity(Gravity.CENTER)
        }
    }

    fun showDialog() {
        if (!isShowing) show()
    }

    /**
     * 清理资源，建议在不需要 Dialog 时调用
     */
    fun release() {
        if (isShowing) dismiss()
        // 移除布局监听器，避免内存泄漏
        starLayout?.let { layout ->
            globalLayoutListener?.let {
                layout.viewTreeObserver.removeOnGlobalLayoutListener(it)
            }
        }
        onClickListener = null
    }

    override fun dismiss() {
        // 自动清理资源
        starLayout?.let { layout ->
            globalLayoutListener?.let {
                layout.viewTreeObserver.removeOnGlobalLayoutListener(it)
            }
        }
        super.dismiss()
    }

    fun setOnRateClickListener(listener: OnRateClickListener) {
        this.onClickListener = listener
    }

    interface OnRateClickListener {
        /**
         * 点击提交按钮时回调
         * @param rating 当前点亮的星星数量（0-5）
         */
        fun onRateClicked(rating: Int)
    }
}