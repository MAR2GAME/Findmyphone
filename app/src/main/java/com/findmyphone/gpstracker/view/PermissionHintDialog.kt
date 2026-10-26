package com.findmyphone.gpstracker.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AdLimitManager
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.ad.ShowNativeAd
import com.findmyphone.gpstracker.base.command.dp2px
import com.findmyphone.gpstracker.base.command.safeClick
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

class PermissionHintDialog(context: Context) : Dialog(context) {
    private var v: View? = null
    private var isGetBackgroundPermission: Boolean=false
    init {
        var inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_loc, null)
        setContentView(v!!)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        val params = this.window!!.attributes
        params.width = dp2px(context, 320)
        params.height =  WindowManager.LayoutParams.WRAP_CONTENT
        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        this.window!!.attributes = params
        this.window!!.setGravity(Gravity.CENTER)
        v!!.findViewById<Button>(R.id.bt_next).safeClick {
            dismiss()
            if(mOnClickListener!=null){
                mOnClickListener!!.onNext(isGetBackgroundPermission)
            }
        }
    }
    fun toShow(hint: String,isGetBackgroundPermission: Boolean) {
        if (v == null) {
            return
        }
        this.isGetBackgroundPermission=isGetBackgroundPermission
        v!!.findViewById<TextView>(R.id.tv_hint).text=hint
        if(isGetBackgroundPermission){
            v!!.findViewById<TextView>(R.id.tv_title).text=context.getString(R.string.access_location_sharing)
            v!!.findViewById<TextView>(R.id.tv_hint).setTextColor("#FE5653".toColorInt())
            v!!.findViewById<TextView>(R.id.tv_title).setTextColor("#FE5653".toColorInt())
            v!!.findViewById<Button>(R.id.bt_next).background=context.getDrawable(R.drawable.btn_del_selector)
            v!!.findViewById<ImageView>(R.id.iv_hint).setImageResource(R.mipmap.iv_hint)
        }else{
            v!!.findViewById<TextView>(R.id.tv_title).text=context.getString(R.string.location_permission)
            v!!.findViewById<TextView>(R.id.tv_hint).setTextColor("#1a1a1a".toColorInt())
            v!!.findViewById<TextView>(R.id.tv_title).setTextColor("#1a1a1a".toColorInt())
            v!!.findViewById<Button>(R.id.bt_next).background=context.getDrawable(R.drawable.btn_selector)
        }
        if (!isShowing) {
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






    private var timerJob: Job? = null // 用于控制定时器的协程任务
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        nativeAd?.destroy()
        nativeAd = null
    }

    private fun startTimer() {
        // 如果已有定时器在运行，先取消
        stopTimer()
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive&& isShowing) { // 循环直到协程被取消
                getNativeAd()
                delay(AppPrefsUtils.getLong("native_refresh_time", 30) * 1000)
            }
        }
    }

    var nativeAd: NativeAd?=null
    suspend fun getNativeAd() {
        var canShowAd = AdLimitManager.getInstance().canShowNativeAd(AreaKey.locationPermissionDialogNativeAdv)
        if(!canShowAd){
            findViewById<NativeAdView>(R.id.native_ad_view)?.visibility = View.GONE
            return
        }
        if (!isShowing) return
        // 销毁旧的 NativeAd
        nativeAd?.destroy()
        val currentActivity = ActivityManagerUtils.currentActivity()
        val currentView = v
        if (currentActivity == null || currentView == null) return
        // 获取新广告
        nativeAd = ShowNativeAd.getNativeAd(currentActivity, AreaKey.locationPermissionDialogNativeAdv)
        // 安全获取 NativeAdView
        val nativeAdView = currentView.findViewById<NativeAdView>(R.id.native_ad_view) ?: return
        if (nativeAd == null) {
            nativeAdView.visibility = View.GONE
        } else {
            nativeAdView.visibility = View.VISIBLE
            setNativeAdView(nativeAd!!, nativeAdView)
        }
    }


    fun setNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        // 获取自定义布局中的视图
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        val callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)

        val iconView =  adView.findViewById<ImageView>(R.id.ad_icon)
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
        // 将广告资产与视图关联
        adView.headlineView = headlineView
        adView.callToActionView = callToActionView
        adView.adChoicesView=adChoicesView
        // 填充数据
        headlineView.text = nativeAd.headline
        if (nativeAd.callToAction != null) {
            callToActionView.text = nativeAd.callToAction
            callToActionView.visibility = Button.VISIBLE
        } else {
            callToActionView.visibility = Button.GONE
        }
        // 必须设置 AdChoices 视图
        adView.setNativeAd(nativeAd)

    }


    override fun show() {
        super.show()
        startTimer() // 显示时启动定时器
    }

    override fun dismiss() {
        stopTimer()   // 关闭前停止定时器
        super.dismiss()
    }


    private var mOnClickListener: onClickListener? = null
    interface onClickListener {
        fun onNext(isGetBackgroundPermission: Boolean) //void
    }
    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }
}