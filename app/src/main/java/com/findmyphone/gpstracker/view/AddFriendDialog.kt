package com.findmyphone.gpstracker.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AdLimitManager
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.ad.ShowNativeAd
import com.findmyphone.gpstracker.base.command.safeClick
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AddFriendDialog(context: Context) : Dialog(context) {
    private var v: View? = null

    init {
        val inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_addfriend, null)
        setContentView(v!!)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        val params = this.window!!.attributes
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        this.window!!.attributes = params
        this.window!!.setGravity(Gravity.BOTTOM)

        v!!.findViewById<RelativeLayout>(R.id.rl_add).safeClick {
            if (mOnClickListener != null) {
                mOnClickListener!!.onAdd()
            }
            dismiss()
        }
        v!!.findViewById<RelativeLayout>(R.id.rl_share).safeClick {
            if (mOnClickListener != null) {
                mOnClickListener!!.onShare()
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
        val canShowAd = AdLimitManager.getInstance().canShowNativeAd(AreaKey.addFirstDialogBtmNativeAdv)
        if (!canShowAd) {
            findViewById<NativeAdView>(R.id.native_ad_view)?.visibility = View.GONE
            return
        }
        if (!isShowing) return

        nativeAd?.destroy()
        val currentActivity = ActivityManagerUtils.currentActivity()
        val currentView = v
        if (currentActivity == null || currentView == null) return

        nativeAd = ShowNativeAd.getNativeAd(currentActivity, AreaKey.addFirstDialogBtmNativeAdv)
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
        val callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)
        val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        val adChoicesView = adView.findViewById<com.google.android.gms.ads.nativead.AdChoicesView>(R.id.ad_choices)

        // 媒体内容处理
        val mediaContent = nativeAd.mediaContent
        if (mediaContent != null) {
            mediaView.visibility = View.VISIBLE
            adView.mediaView = mediaView
            mediaView.mediaContent = mediaContent
        } else {
            mediaView.visibility = View.GONE
            adView.mediaView = null // 解除关联
        }

        // 正文处理
        adView.bodyView = bodyView
        if (nativeAd.body != null) {
            bodyView.text = nativeAd.body
            bodyView.visibility = TextView.VISIBLE
        } else {
            bodyView.visibility = TextView.GONE
        }

        // 关联所有视图
        adView.headlineView = headlineView
        adView.callToActionView = callToActionView
        adView.adChoicesView = adChoicesView

        // 填充数据
        headlineView.text = nativeAd.headline
        if (nativeAd.callToAction != null) {
            callToActionView.text = nativeAd.callToAction
            callToActionView.visibility = Button.VISIBLE
        } else {
            callToActionView.visibility = Button.GONE
        }

        // 绑定原生广告
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

    fun toShow() {
        if (v == null) return
        if (!isShowing) show()
    }

    fun release() {
        if (isShowing) dismiss()
        mOnClickListener = null
        v = null
    }



    private var mOnClickListener: onClickListener? = null

    interface onClickListener {
        fun onShare()
        fun onAdd()
    }
    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }
}