package com.findmyphone.gpstracker.view


import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.findmyphone.gpstracker.R
import com.google.android.libraries.places.api.model.AutocompletePrediction

import kotlin.math.min
import kotlin.ranges.coerceAtLeast
import kotlin.ranges.coerceAtMost
import kotlin.text.isNullOrEmpty

class LocationSearchPopupWindow(
    context: Context,
    private val predictions: List<AutocompletePrediction>,
    private val onItemClick: (AutocompletePrediction) -> Unit
) : PopupWindow(context) {

    private val recyclerView: RecyclerView
    private val adapter: SearchResultAdapter

    init {
        // 正常初始化，不再对空列表做特殊处理（避免 return 编译错误）
        val view = LayoutInflater.from(context).inflate(R.layout.popup_location_search, null)
        recyclerView = view.findViewById(R.id.rv_search_results)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = SearchResultAdapter(predictions) { prediction ->
            dismiss()
            onItemClick(prediction)
        }
        recyclerView.adapter = adapter

        contentView = view
        width = ViewGroup.LayoutParams.MATCH_PARENT
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        isFocusable = true
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        animationStyle =android.R.style.Animation_Dialog
    }

    fun show(anchorView: View) {
        // 空列表直接不显示弹窗
        if (predictions.isEmpty()) return

        // 隐藏软键盘
        val imm = anchorView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(anchorView.windowToken, 0)

        anchorView.post {
            // 动态计算最佳宽度
            val screenWidth = anchorView.context.resources.displayMetrics.widthPixels
            val maxWidth = screenWidth - dp2px(anchorView.context, 32)
            val desiredWidth = if (anchorView.width > 0) {
                anchorView.width + dp2px(anchorView.context, 80)
            } else {
                screenWidth - dp2px(anchorView.context, 64)
            }
            width = min(desiredWidth, maxWidth).coerceAtLeast(dp2px(anchorView.context, 240))

            // 动态测量第一个 item 的真实高度
            val itemHeight = measureRealItemHeight(anchorView.context)
            val estimatedHeight = (adapter.itemCount * itemHeight).coerceAtMost(dp2px(anchorView.context, 350))

            // 计算屏幕位置，避免超出屏幕
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            val screenHeight = anchorView.context.resources.displayMetrics.heightPixels
            val yOffset = if (location[1] + anchorView.height + estimatedHeight > screenHeight) {
                -(anchorView.height + estimatedHeight)
            } else {
                0
            }
            showAsDropDown(anchorView, 0, yOffset + dp2px(anchorView.context, 10))
        }
    }

    /**
     * 测量 RecyclerView 第一个 item 的真实高度（如果列表不为空）
     */
    private fun measureRealItemHeight(context: Context): Int {
        if (adapter.itemCount == 0) return dp2px(context, 70)
        val itemView = adapter.onCreateViewHolder(recyclerView, 0).itemView
        itemView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return itemView.measuredHeight
    }

    private fun dp2px(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private class SearchResultAdapter(
        private val predictions: List<AutocompletePrediction>,
        private val onItemClick: (AutocompletePrediction) -> Unit
    ) : RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_location_search_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val prediction = predictions[position]
            holder.bind(prediction)
            holder.itemView.setOnClickListener { onItemClick(prediction) }
        }

        override fun getItemCount(): Int = predictions.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvPrimaryText: TextView = itemView.findViewById(R.id.tv_primary_text)
            private val tvSecondaryText: TextView = itemView.findViewById(R.id.tv_secondary_text)

            fun bind(prediction: AutocompletePrediction) {
                tvPrimaryText.text = prediction.getPrimaryText(null).toString()
                val secondaryText = prediction.getSecondaryText(null)?.toString()
                if (secondaryText.isNullOrEmpty()) {
                    tvSecondaryText.visibility = View.GONE
                } else {
                    tvSecondaryText.visibility = View.VISIBLE
                    tvSecondaryText.text = secondaryText
                }
            }
        }
    }
}