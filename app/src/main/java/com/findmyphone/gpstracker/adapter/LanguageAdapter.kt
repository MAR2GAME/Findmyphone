package com.findmyphone.gpstracker.adapter


import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getDrawable
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.findmyphone.gpstracker.R


class  LanguageAdapter(data: MutableList<String>) :
    BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_language, data) {

    private var selectedPosition = -1

    // 缓存资源，避免每次 convert 重复获取
    private var selectedTextColor: Int? = null
    private var normalTextColor: Int? = null
    private var selectedCheckIcon: Int? = null
    private var normalCheckIcon: Int? = null
    private var selectedBgDrawable: Drawable? = null
    private var normalBgDrawable: Drawable? = null

    /**
     * 设置选中的语言 key（即语言名称），并刷新对应条目
     */
    fun setSelectedKey(key: String) {
        val newPosition = data.indexOf(key)
        if (newPosition == selectedPosition) return
        val oldPosition = selectedPosition
        selectedPosition = newPosition
        // 只刷新变化的两个条目
        if (oldPosition >= 0) notifyItemChanged(oldPosition)
        if (newPosition >= 0) notifyItemChanged(newPosition)
    }

    override fun convert(holder: BaseViewHolder, item: String) {
        val context = holder.itemView.context
        // 懒加载资源（仅第一次使用时加载）
        if (selectedTextColor == null) {
            selectedTextColor = getColor(context, R.color.white)
            normalTextColor = getColor(context, R.color.title_color)
            selectedCheckIcon = R.mipmap.ic_check
            normalCheckIcon = R.mipmap.ic_check_nol
            selectedBgDrawable = getDrawable(context, R.drawable.language_check__shape)
            normalBgDrawable = getDrawable(context, R.drawable.language_normal_shape)
        }

        val tvLanguage = holder.getView<TextView>(R.id.tv_language)
        val ivCheck = holder.getView<ImageView>(R.id.iv_check)
        val tvBg = holder.getView<TextView>(R.id.tv_bg)
        tvLanguage.text = item
        val position = holder.bindingAdapterPosition
        val isSelected = position != RecyclerView.NO_POSITION && position == selectedPosition
        if (isSelected) {
            tvLanguage.setTextColor(selectedTextColor!!)
            ivCheck.setImageResource(selectedCheckIcon!!)
            tvBg.background = selectedBgDrawable
        } else {
            tvLanguage.setTextColor(normalTextColor!!)
            ivCheck.setImageResource(normalCheckIcon!!)
            tvBg.background = normalBgDrawable
        }
    }
}