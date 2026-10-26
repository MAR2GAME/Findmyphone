package com.findmyphone.gpstracker.ad

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.findmyphone.gpstracker.ad.pool.AdPool
import com.findmyphone.gpstracker.ad.pool.MyAdLoader
import com.findmyphone.gpstracker.advertise.AdvertisePageMediator
import com.findmyphone.gpstracker.advertise.CallbackOnce
import com.findmyphone.gpstracker.advertise.InterstitialRequestGate
import com.findmyphone.gpstracker.base.command.checkNotificationPermission
import com.findmyphone.gpstracker.base.command.hasLocationPermission
import com.findmyphone.gpstracker.base.command.readyGo
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.ui.NativeAdActivity
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.LocaleHelper
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.view.ProgressDialog

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import kotlin.jvm.java
open class AdvActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.setLocaleForApplication(newBase))
    }

    var progressDialog: ProgressDialog? = null

    private val interstitialRequestGate = InterstitialRequestGate()
    private var idleJob: Job? = null
    fun handleLoadingState(showLoading: Boolean) {
        if (progressDialog == null) {
            progressDialog = ProgressDialog(this)
        }
        if (showLoading) {
            progressDialog?.showProgress()
        } else {
            progressDialog?.hideProgress()
        }
    }


    override fun onResume() {
        super.onResume()
        if(!AppConfig.notShowNativeAdClazz.contains(this.javaClass)){
            resetIdleTimer()
        }
    }
    override fun onPause() {
        super.onPause()
        idleJob?.cancel()
        idleJob = null
    }
    private fun resetIdleTimer() {
        // 取消之前的协程
        idleJob?.cancel()
        var canShowAd = AdLimitManager.getInstance().canShowNativeAd(AreaKey.openMainNativeAdv)
        if (!canShowAd) {
            return
        }
        // 启动新的协程
        idleJob = lifecycleScope.launch {
            while (true) {
                delay(AppPrefsUtils.getLong("show_main_bottom_native_time", 10) * 1000) //
                readyGo(NativeAdActivity::class.java)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityManagerUtils.addActivity(this)
    }

    public fun showInterstitialAdWithStats(areaKey: String, onClosed: () -> Unit) {
        if (isFinishing || isDestroyed || !interstitialRequestGate.tryAcquire()) return
        handleLoadingState(true)
        val closeOnce = CallbackOnce {
            handleLoadingState(false)
            lifecycleScope.launch {
                delay(100)
                try {
                    onClosed()
                } finally {
                    interstitialRequestGate.release()
                }
            }
        }
        AdvertisePageMediator.showInterstitial(this, areaKey) { closeOnce.invoke() }
    }

    override fun onDestroy() {
        progressDialog?.hideProgress()
        progressDialog = null
        interstitialRequestGate.release()
        ActivityManagerUtils.removeActivity(this)
        logUserData()
        if (ActivityManagerUtils.activities.size == 1) {
            MyAdLoader.cleanup()
            AdPool.clearAllPools()
        }
        super.onDestroy()
    }

    private fun logUserData() {
        val time = System.currentTimeMillis()
        val userData = mapOf(
            "my_latest_open_time" to getDetailCurrentTime(time),
            "has_notification_permission" to checkNotificationPermission(this),
            "has_location_permission" to hasLocationPermission(true)
        )
        LogUtil.setUser(userData, "user_set")
        LogUtil.setUser(mapOf("total_open_num" to 1), "user_add")
        LogUtil.setUser(mapOf("my_total_use_time" to getDetailCurrentTime(time - AppPrefsUtils.getLong(AppConfig.START_TIME))), "user_add")
    }



    fun getDetailCurrentTime(time: Long): String {
        val date = Date(time)
        val strDateFormat = "yyyy-MM-dd HH:mm:ss"
        val format = SimpleDateFormat(strDateFormat)
        format.timeZone = TimeZone.getDefault()
        return format.format(date)
    }
}
