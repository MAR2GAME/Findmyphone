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


class NoFriendDialog  (context: Context) : Dialog(context) {
    private var v: View? = null
    init {
        var inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_no_friend, null)
        setContentView(v!!)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        val params = this.window!!.attributes
        params.width = dp2px(context, 320)
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        this.window!!.attributes = params
        this.window!!.setGravity(Gravity.CENTER)
        v!!.findViewById<Button>(R.id.bt_next).safeClick {
            if (mOnClickListener != null) {
                mOnClickListener!!.onNext()
            }
            dismiss()
        }

    }

    fun toShow() {
        if (v == null) {
            return
        }
        if (!isShowing) {
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
        fun onNext() //void
    }
    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }
}