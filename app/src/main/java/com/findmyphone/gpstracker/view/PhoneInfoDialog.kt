package com.findmyphone.gpstracker.view

import android.R.attr.data
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.command.safeClick
import com.findmyphone.gpstracker.bean.InfoItem
import com.google.android.material.imageview.ShapeableImageView

import kotlin.let

class PhoneInfoDialog(context: Context) : Dialog(context) {
    private lateinit var phoneInfoAdapter: BaseQuickAdapter<InfoItem, BaseViewHolder>
    private var v: View? = null

    init {
        var inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_phoneinfo, null)
        setContentView(v!!)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        val params = this.window!!.attributes
        params.width =WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        this.window!!.attributes = params
        this.window!!.setGravity(Gravity.BOTTOM)

        var recyclerView = v!!.findViewById<RecyclerView>(R.id.rc_info)
        phoneInfoAdapter =
            object : BaseQuickAdapter<InfoItem, BaseViewHolder>(R.layout.item_info) {
                override fun convert(
                    holder: BaseViewHolder,
                    item: InfoItem
                ) {
                    item.let {
                        holder.setText(R.id.tv_name,it.tagName)
                        holder.setText(R.id.tvValue,it.info)
                    }
                }
            }
        recyclerView.adapter = phoneInfoAdapter
        recyclerView.layoutManager= LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        v!!.findViewById<Button>(R.id.bt_next).safeClick {
            dismiss()
            if(mOnClickListener!=null){
                mOnClickListener!!.onNext()
            }

        }
        v!!.findViewById<ImageView>(R.id.iv_back).safeClick {
            dismiss()
            if(mOnClickListener!=null){
                mOnClickListener!!.onNext()
            }
        }
    }
    fun toShow(number:String,data: MutableList<InfoItem>) {
        if (v == null) {
            return
        }
        if (!isShowing) {
            v!!.findViewById<TextView>(R.id.tv_number).text=number
            phoneInfoAdapter.setList(data)
            show()
        }
    }

    fun release() {
        if (isShowing) {
            dismiss()
        }
        mOnClickListener=null
        v = null
    }

    private var mOnClickListener: onClickListener? = null
    interface onClickListener {
        fun onNext()
    }
    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }
}