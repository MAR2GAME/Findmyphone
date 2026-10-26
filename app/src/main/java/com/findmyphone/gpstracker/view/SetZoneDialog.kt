package com.findmyphone.gpstracker.view

import android.R.attr.bitmap
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.findmyphone.gpstracker.R

import kotlin.text.trim

class SetZoneDialog (context: Context) : Dialog(context) {
    private var v: View? = null

    private var enterCheck=true

    private var leaveCheck=true

    init {
        var inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_set_zone, null)
        setContentView(v!!)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        val params = this.window!!.attributes
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        this.window!!.attributes = params
        this.window!!.setGravity(Gravity.BOTTOM)
        v!!.findViewById<LinearLayout>(R.id.ll_enter_check).setOnClickListener {
            enterCheck=!enterCheck
            if(enterCheck){
                v!!.findViewById<ImageView>(R.id.iv_enter_check).setImageResource(R.mipmap.ic_zone_check)
            }else{
                v!!.findViewById<ImageView>(R.id.iv_enter_check).setImageResource(R.mipmap.ic_zone_check_nor)
            }
        }
        v!!.findViewById<LinearLayout>(R.id.ll_leave_check).setOnClickListener {
            leaveCheck=!leaveCheck
            if(leaveCheck){
                v!!.findViewById<ImageView>(R.id.iv_leave_check).setImageResource(R.mipmap.ic_zone_check)
            }else{
                v!!.findViewById<ImageView>(R.id.iv_leave_check).setImageResource(R.mipmap.ic_zone_check_nor)
            }
        }
        v!!.findViewById<Button>(R.id.bt_next).setOnClickListener {
            var name= v!!.findViewById<EditText>(R.id.et_name).text.toString().trim()
            if(TextUtils.isEmpty(name)){
                Toast.makeText(context,context.getString(R.string.enter_zone_name),Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if(mOnClickListener!=null){
                mOnClickListener!!.onSave(name,enterCheck,leaveCheck)
            }
            dismiss()
        }
        v!!.findViewById<ImageView>(R.id.iv_delete).setOnClickListener {
            if(mOnClickListener!=null){
                mOnClickListener!!.onDelete()
            }
            dismiss()
        }
    }



    fun toShow(name:String,type:Int,enterCheck: Boolean=true,leaveCheck: Boolean=true) {
        if (v == null) {
            return
        }
        if (!isShowing) {
            this.enterCheck=enterCheck
            this.leaveCheck=leaveCheck
            v!!.findViewById<EditText>(R.id.et_name).setText(name)
            v!!.findViewById<ImageView>(R.id.iv_delete).isVisible=type==1
            if(enterCheck){
                v!!.findViewById<ImageView>(R.id.iv_enter_check).setImageResource(R.mipmap.ic_zone_check)
            }else{
                v!!.findViewById<ImageView>(R.id.iv_enter_check).setImageResource(R.mipmap.ic_zone_check_nor)
            }
            if(leaveCheck){
                v!!.findViewById<ImageView>(R.id.iv_leave_check).setImageResource(R.mipmap.ic_zone_check)
            }else{
                v!!.findViewById<ImageView>(R.id.iv_leave_check).setImageResource(R.mipmap.ic_zone_check_nor)
            }
            show()
        }
    }

    fun release() {
        if (isShowing) {
            dismiss()
        }
        mOnClickListener = null
        v = null
    }

    private var mOnClickListener: onClickListener? = null

    interface onClickListener {
        fun onSave(name:String,enterCheck:Boolean,leaveCheck:Boolean=true)
        fun onDelete()
    }

    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }
}