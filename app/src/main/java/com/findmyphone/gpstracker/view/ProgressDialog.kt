package com.findmyphone.gpstracker.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import com.findmyphone.gpstracker.base.command.dp2px
import com.findmyphone.gpstracker.databinding.DialogProgressBinding

import kotlin.apply


class ProgressDialog(context: Context) : Dialog(context) {

    // 使用 ViewBinding 替代 findViewById
    private val binding: DialogProgressBinding = DialogProgressBinding.inflate(LayoutInflater.from(context))
    init {
        setContentView(binding.root)
        initializeDialog()
    }
    private fun initializeDialog() {
        // 设置对话框属性
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        // 配置窗口属性
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setGravity(Gravity.CENTER)
            val size = dp2px(context, DIALOG_SIZE_DP)
            attributes = attributes.apply {
                width = size
                height = size
            }
        }
    }

    fun showProgress() {
        if (!isShowing) {
            show()
        }
        binding.rotatingRingView.visibility = View.VISIBLE
    }

    fun hideProgress() {
        binding.rotatingRingView.visibility = View.GONE
        if (isShowing) {
            dismiss()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 清理资源
        binding.rotatingRingView.visibility = View.GONE
    }

    companion object {
        private const val DIALOG_SIZE_DP = 140
    }
}