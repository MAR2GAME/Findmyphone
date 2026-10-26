package com.findmyphone.gpstracker.view

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AdLimitManager
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.ad.ShowNativeAd
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.times

class UserCodeDialog(context: Context) : Dialog(context) {
    private var v: View? = null
    private var isCheck = true

    init {
        val inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dlialog_usercode, null)
        setContentView(v!!)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        val params = this.window!!.attributes
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        this.window!!.attributes = params
        this.window!!.setGravity(Gravity.BOTTOM)

        v!!.findViewById<ImageView>(R.id.iv_close).setOnClickListener {
            dismiss()
        }
        v!!.findViewById<ImageView>(R.id.iv_check).setOnClickListener {
            isCheck = !isCheck
            if (isCheck) {
                v!!.findViewById<ImageView>(R.id.iv_check).setImageResource(R.mipmap.iv_check)
            } else {
                v!!.findViewById<ImageView>(R.id.iv_check).setImageResource(R.mipmap.iv_check_nor)
            }
        }
        v!!.findViewById<ImageView>(R.id.iv_share).setOnClickListener {
            if (mOnClickListener != null) {
                mOnClickListener!!.onShare()
            }
        }
        v!!.findViewById<ImageView>(R.id.iv_copy).setOnClickListener {
            if (mOnClickListener != null) {
                mOnClickListener!!.onCopy()
            }
        }
        v!!.findViewById<Button>(R.id.bt_add).setOnClickListener {
            if (mOnClickListener != null) {
                mOnClickListener!!.onAdd()
            }
            dismiss()
        }
    }



    // ---------- 广告定时刷新 ----------
    private var timerJob: Job? = null
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        nativeAd?.destroy()
        nativeAd = null
    }

    private fun startTimer() {
        stopTimer()
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && isShowing) {
                getNativeAd()
                delay(AppPrefsUtils.getLong("native_refresh_time", 30) * 1000)
            }
        }
    }

    var nativeAd: NativeAd? = null

    suspend fun getNativeAd() {
        val canShowAd = AdLimitManager.getInstance().canShowNativeAd(AreaKey.connectDialogBtmNativeAdv)
        if (!canShowAd) {
            findViewById<NativeAdView>(R.id.native_ad_view)?.visibility = View.GONE
            return
        }
        if (!isShowing) return

        nativeAd?.destroy()
        val currentActivity = ActivityManagerUtils.currentActivity()
        val currentView = v
        if (currentActivity == null || currentView == null) return

        nativeAd = ShowNativeAd.getNativeAd(currentActivity, AreaKey.connectDialogBtmNativeAdv)
        val nativeAdView = currentView.findViewById<NativeAdView>(R.id.native_ad_view) ?: return
        if (nativeAd == null) {
            nativeAdView.visibility = View.GONE
        } else {
            nativeAdView.visibility = View.VISIBLE
            setNativeAdView(nativeAd!!, nativeAdView)
        }
    }

    fun setNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        // 获取视图
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        // 将 CTA 从 Button 改为 TextView，避免溢出
        val callToActionView = adView.findViewById<TextView>(R.id.ad_call_to_action)
        val iconView = adView.findViewById<ImageView>(R.id.ad_icon)
        val bodyView = adView.findViewById<TextView>(R.id.ad_body)

        adView.iconView = iconView
        adView.bodyView = bodyView

        if (nativeAd.icon != null) {
            iconView.setImageDrawable(nativeAd.icon?.drawable)
            iconView.visibility = ImageView.VISIBLE
        } else {
            iconView.visibility = ImageView.GONE
        }
        if (nativeAd.body != null) {
            bodyView.text = nativeAd.body
            bodyView.visibility = TextView.VISIBLE
        } else {
            bodyView.visibility = TextView.GONE
        }

        val adChoicesView = adView.findViewById<com.google.android.gms.ads.nativead.AdChoicesView>(R.id.ad_choices)

        // 关联视图
        adView.headlineView = headlineView
        adView.callToActionView = callToActionView
        adView.adChoicesView = adChoicesView

        // 填充数据
        headlineView.text = nativeAd.headline
        if (nativeAd.callToAction != null) {
            callToActionView.text = nativeAd.callToAction
            callToActionView.visibility = TextView.VISIBLE   // 使用 TextView.VISIBLE
        } else {
            callToActionView.visibility = TextView.GONE
        }

        adView.setNativeAd(nativeAd)
    }

    override fun show() {
        super.show()
        startTimer()
    }

    override fun dismiss() {
        stopTimer()
        super.dismiss()
    }

    fun toShow(code: String, bitmap: Bitmap) {
        if (v == null) return
        if (!isShowing) {
            show()
            v!!.findViewById<TextView>(R.id.tv_code).text = code
            Glide.with(context).load(bitmap).into(v!!.findViewById<ImageView>(R.id.iv_code))
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
        fun onShare()
        fun onCopy()
        fun onAdd()
    }

    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }
}