package com.findmyphone.gpstracker.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.command.dp2px
import com.findmyphone.gpstracker.base.command.safeClick


class SetLanguageDialog(context: Context) : Dialog(context) {
    private var v: View? = null

    init {
        var inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_setlanguage, null)
        setContentView(v!!)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        val params = this.window!!.attributes
        params.width = dp2px(context, 320)
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        this.window!!.attributes = params
        this.window!!.setGravity(Gravity.CENTER)
        v!!.findViewById<Button>(R.id.bt_confirm).safeClick {
            if (mOnClickListener != null) {
                mOnClickListener!!.onNext()
            }
            dismiss()
        }
        v!!.findViewById<TextView>(R.id.tv_cancel).safeClick {
            dismiss()
        }
    }

    fun toShow(name:String) {
        if (v == null) {
            return
        }
        v!!.findViewById<TextView>(R.id.tv_title).text=name
        if (!isShowing) {
            show()
        }
    }

    fun relese() {
        if (isShowing) {
            dismiss()
        }
        mOnClickListener = null
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