package com.findmyphone.gpstracker.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import cn.thinkingdata.analytics.TDAnalytics
import com.applovin.mediation.MaxAd
import com.findmyphone.gpstracker.ad.LogAdData
import com.findmyphone.gpstracker.ad.LogAdParam
import com.findmyphone.gpstracker.base.MyApplication

import com.google.android.gms.ads.AdValue
import com.google.firebase.analytics.FirebaseAnalytics


import org.json.JSONObject
import kotlin.apply

object LogUtil {
    private const val TAG = "LogUtil"
    private var jsonObject = JSONObject()

    fun log(eventName: String, params: Map<String, Any>) {
        Log.e(TAG, "log: $eventName $params")
        if (eventName != LogAdData.ad_impression) {
            logFirebase(eventName, params)
        }
        if(eventName !="notification_shown"){
            logThinking(eventName, params)
        }
    }
    @SuppressLint("MissingPermission")
    fun logFirebase(eventName: String, params: Map<String, Any>) {
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(appContext())
            val bundle = Bundle()
            if (params == null || params.isEmpty()) {
                // 如果没有参数，只发送事件名
                firebaseAnalytics.logEvent(eventName, bundle)
                return
            }
            // 同步访问params
            synchronized(params) {
                for ((key, value) in params) {
                    when (value) {
                        is String -> bundle.putString(key, value)
                        is Int -> bundle.putInt(key, value)
                        is Long -> bundle.putLong(key, value)
                        is Double -> bundle.putDouble(key, value)
                        is Float -> bundle.putFloat(key, value)
                        is Boolean -> bundle.putBoolean(key, value)
                        else -> bundle.putString(key, value.toString())
                    }
                }
            }
            firebaseAnalytics.logEvent(eventName, bundle)
        }catch (e: Exception){

        }
    }


    fun logThinking(eventName: String, params: Map<String, Any>) {
        try {
            jsonObject = JSONObject()
            for ((key, value) in params) {
                jsonObject.put(key, value)
            }
            TDAnalytics.track(eventName, jsonObject)
        } catch (e: Exception) {
            Log.e(TAG, "logThinking error: ${e.message}")
        }
    }


    fun setUser(params: Map<String, Any>, tag: String) {
        try {
            val jsonObject = JSONObject()
            for ((key, value) in params) {
                jsonObject.put(key, value)
            }
            when (tag) {
                "user_setOnce" -> {
                    TDAnalytics.userSetOnce(jsonObject)
                }

                "user_set" -> {
                    TDAnalytics.userSet(jsonObject)
                }

                "user_add" -> {
                    TDAnalytics.userAdd(jsonObject)
                }
            }
        } catch (e: Exception) {
        }
    }


    @SuppressLint("MissingPermission")
    fun logTaiChiAdmob(adValue: AdValue) {
        val mFirebaseAnalytics = FirebaseAnalytics.getInstance(appContext())
        val currentImpressionRevenue = adValue.valueMicros.toDouble() / 1_000_000.0
        val precisionType = when (adValue.precisionType) {
            0 -> "UNKNOWN"
            1 -> "ESTIMATED"
            2 -> "PUBLISHER_PROVIDED"
            3 -> "PRECISE"
            else -> "Invalid"
        }
        val params = Bundle().apply {
            putDouble(FirebaseAnalytics.Param.VALUE, currentImpressionRevenue)
            putString(FirebaseAnalytics.Param.CURRENCY, LogAdParam.USD)
            putString("precisionType", precisionType)
        }
        mFirebaseAnalytics.logEvent(LogAdData.ad_Impression_Revenue, params) // 给Taichi用
        val previousTaichiTroasCache = AppPrefsUtils.getFloat(LogAdParam.admobTaichiTroasCache, 0f)
        val currentTaichiTroasCache = previousTaichiTroasCache + currentImpressionRevenue.toFloat()

        if (currentTaichiTroasCache >= 0.01f) {
            val roasBundle = Bundle().apply {
                putDouble(FirebaseAnalytics.Param.VALUE, currentTaichiTroasCache.toDouble())
                putString(FirebaseAnalytics.Param.CURRENCY, LogAdParam.USD)
            }
            mFirebaseAnalytics.logEvent(LogAdData.total_Ads_Revenue_001, roasBundle)
            AppPrefsUtils.commitFloat(LogAdParam.admobTaichiTroasCache, 0f)
        } else {
            AppPrefsUtils.commitFloat(LogAdParam.admobTaichiTroasCache, currentTaichiTroasCache)
        }
    }

    @SuppressLint("MissingPermission")
    fun logTaiChiMax(impressionData: MaxAd) {
        val mFirebaseAnalytics = FirebaseAnalytics.getInstance(appContext())
        val currentImpressionRevenue = impressionData.revenue // USD单位
        val params = Bundle().apply {
            putString(FirebaseAnalytics.Param.AD_PLATFORM, LogAdParam.appLovin)
            putString(FirebaseAnalytics.Param.AD_SOURCE, impressionData.networkName)
            putString(FirebaseAnalytics.Param.AD_FORMAT, impressionData.format.displayName)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, impressionData.adUnitId)
            putDouble(FirebaseAnalytics.Param.VALUE, currentImpressionRevenue)
            putString(FirebaseAnalytics.Param.CURRENCY, LogAdParam.USD)
        }

        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params) // 给ARO用
        mFirebaseAnalytics.logEvent(LogAdData.ad_Impression_Revenue, params) // 给Taichi用

        val previousTaichiTroasCache = AppPrefsUtils.getFloat(LogAdParam.taichiMaxTroasCache, 0f)
        val currentTaichiTroasCache = previousTaichiTroasCache + currentImpressionRevenue.toFloat()

        if (currentTaichiTroasCache >= 0.01f) {
            val roasBundle = Bundle().apply {
                putDouble(FirebaseAnalytics.Param.VALUE, currentTaichiTroasCache.toDouble())
                putString(FirebaseAnalytics.Param.CURRENCY, LogAdParam.USD)
            }
            mFirebaseAnalytics.logEvent(LogAdData.total_Ads_Revenue_001, roasBundle) // 给Taichi用
            AppPrefsUtils.commitFloat(LogAdParam.taichiMaxTroasCache, 0f) // 重新清零
        } else {
            AppPrefsUtils.commitFloat(LogAdParam.taichiMaxTroasCache, currentTaichiTroasCache) // 缓存
        }
    }


    var isOpenLog = true

    fun isOpenLog(isDebug: Boolean) {
        isOpenLog = isDebug
    }

    fun d(content: String?) {
        if (!isOpenLog) return
        if (content != null) {
            Log.d(TAG, content)
        }
    }

    fun d(content: String?, tr: Throwable?) {
        if (!isOpenLog) return
        if (content != null) {
            Log.d(TAG, content, tr)
        }
    }

    fun e(content: String?) {
        if (!isOpenLog) return
        if (content != null) {
            Log.e(TAG, content)
        }
    }

    fun e(content: String?, tr: Throwable?) {
        if (!isOpenLog) return
        if (content != null) {
            Log.e(TAG, content, tr)
        }
    }

    fun i(content: String?) {
        if (!isOpenLog) return
        if (content != null) {
            Log.i(TAG, content)
        }
    }

    fun i(content: String?, tr: Throwable?) {
        if (!isOpenLog) return
        if (content != null) {
            Log.i(TAG, content, tr)
        }
    }

    fun v(content: String?) {
        if (!isOpenLog) return
        if (content != null) {
            Log.v(TAG, content)
        }
    }

    fun v(content: String?, tr: Throwable?) {
        if (!isOpenLog) return
        if (content != null) {
            Log.v(TAG, content, tr)
        }
    }

    fun w(content: String?) {
        if (!isOpenLog) return
        if (content != null) {
            Log.w(TAG, content)
        }
    }

    fun w(content: String?, tr: Throwable?) {
        if (!isOpenLog) return
        if (content != null) {
            Log.w(TAG, content, tr)
        }
    }


    private fun appContext(): Context {
        try {
            if(MyApplication.instance!=null){
                return  MyApplication.instance!!
            }else{
                val activityThread = Class.forName("android.app.ActivityThread")
                val currentActivityThread = activityThread.getMethod("currentActivityThread").invoke(null)
                val app = activityThread.getMethod("getApplication").invoke(currentActivityThread)
                return  app as Context
            }
        } catch (e: Exception) {
            throw kotlin.IllegalStateException("Application not initialized")
        }
    }
}
