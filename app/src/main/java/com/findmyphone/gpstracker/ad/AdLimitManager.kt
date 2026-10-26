package com.findmyphone.gpstracker.ad

import android.text.TextUtils
import android.util.Log
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.MyApplication
import com.findmyphone.gpstracker.bean.AdLimitConfig
import com.findmyphone.gpstracker.bean.AdRecord
import com.findmyphone.gpstracker.bean.AdUnit
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.google.android.datatransport.runtime.scheduling.SchedulingConfigModule_ConfigFactory.config

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ironsource.adqualitysdk.sdk.i.it
import com.pdffox.adv.AdvertiseSdk



import java.util.concurrent.ThreadLocalRandom
import kotlin.also
import kotlin.collections.count
import kotlin.collections.filter
import kotlin.collections.find
import kotlin.collections.maxByOrNull
import kotlin.collections.toMutableList
import kotlin.io.bufferedReader
import kotlin.io.readText
import kotlin.io.use
import kotlin.jvm.java
import kotlin.let


class AdLimitManager() {
    private val gson = Gson()
    private var config: AdLimitConfig? = null
    private var nativeConfig: AdLimitConfig? = null

    companion object {
        @Volatile
        private var instance: AdLimitManager? = null
        fun getInstance(): AdLimitManager {
            return instance ?: synchronized(this) {
                instance ?: AdLimitManager().also { instance = it }
            }
        }
    }
    fun initConfig() {
        var ad_Policy= if(AdvertiseSdk.isNature){
           AppPrefsUtils.getString("ad_policy_nature")
        }else{
            AppPrefsUtils.getString("ad_policy")
        }
        var old_ad_Policy = AppPrefsUtils.getString("old_ad_Policy")
        if (config == null || ad_Policy != old_ad_Policy) {
            AppPrefsUtils.commitString("old_ad_Policy", ad_Policy)
            try {
                if (!TextUtils.isEmpty(ad_Policy)) {
                    config = gson.fromJson(ad_Policy, AdLimitConfig::class.java)
                }else{
                    var inputStream = if(AdvertiseSdk.isNature){
                        MyApplication.instance.resources.openRawResource(R.raw.ad_policy_audit)
                    }else{
                        MyApplication.instance.resources.openRawResource(R.raw.ad_policy)
                    }
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    config = gson.fromJson(jsonString, AdLimitConfig::class.java)
                }
            } catch (e: Exception) {

            }
        }
    }

    fun initNativeConfig() {
        var ad_Policy= if(AdvertiseSdk.isNature){
            AppPrefsUtils.getString("ad_native_policy_nature")
        }else{
            AppPrefsUtils.getString("ad_native_policy")
        }
        Log.e("weiyu","ad_Policy"+ad_Policy)
        var old_ad_Policy = AppPrefsUtils.getString("old_ad_native_policy")
        if (nativeConfig == null || ad_Policy != old_ad_Policy) {
            AppPrefsUtils.commitString("old_ad_native_policy", ad_Policy)
            try {
                try {
                    if (!TextUtils.isEmpty(ad_Policy)) {
                        nativeConfig = gson.fromJson(ad_Policy, AdLimitConfig::class.java)
                    }else{
                        var inputStream = if(AdvertiseSdk.isNature){
                            MyApplication.instance.resources.openRawResource(R.raw.ad_native_policy_nature)
                        }else{
                            MyApplication.instance.resources.openRawResource(R.raw.ad_native_policy)
                        }
                        val jsonString = inputStream.bufferedReader().use { it.readText() }
                        nativeConfig = gson.fromJson(jsonString, AdLimitConfig::class.java)
                    }
                } catch (e: Exception) {

                }
            } catch (e: Exception) {
            }
        }
    }
    private fun getNativeAllRecords(): MutableList<AdRecord> {
        val json = AppPrefsUtils.getString("native_ad_records", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<AdRecord>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun getAllRecords(): MutableList<AdRecord> {
        val json = AppPrefsUtils.getString("ad_records", "[]") ?: "[]"
        val type = object : TypeToken<MutableList<AdRecord>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    private fun saveRecords(records: MutableList<AdRecord>) {
        val json = gson.toJson(records)
        AppPrefsUtils.commitString("ad_records", json)
    }

    private fun saveNativeRecords(records: MutableList<AdRecord>) {
        val json = gson.toJson(records)
        AppPrefsUtils.commitString("native_ad_records", json)
    }


    // 清理过期记录
    private fun cleanExpiredNativeRecords() {
        val now = System.currentTimeMillis()
        val records = getNativeAllRecords()
        // 清理超过总时间限制的记录
        val validRecords = records.filter {
            now - it.timestamp < nativeConfig!!.limited_loadtime_seconds*1000
        }.toMutableList()
        if (validRecords.size < records.size) {
            saveNativeRecords(validRecords)
        }
    }

    // 清理过期记录
    private fun cleanExpiredRecords() {
        val now = System.currentTimeMillis()
        val records = getAllRecords()

        // 清理超过总时间限制的记录
        val validRecords = records.filter {
            now - it.timestamp < config!!.limited_loadtime_seconds*1000
        }.toMutableList()
        if (validRecords.size < records.size) {
            saveRecords(validRecords)
        }
    }

    // 记录广告展示
    fun recordAdShow(areaKey: String) {
        val records = getAllRecords()
        records.add(AdRecord(System.currentTimeMillis(), areaKey, "show"))
        saveRecords(records)
    }


    // 记录广告展示
    fun recordNativeAdShow() {
        val records = getNativeAllRecords()
        records.add(AdRecord(System.currentTimeMillis(), "", "show"))
        saveNativeRecords(records)
    }

    // 记录广告点击
    fun recordAdClick() {
        val records = getAllRecords()
        records.add(AdRecord(System.currentTimeMillis(), "", "click"))
        saveRecords(records)
    }


    // 记录广告点击
    fun recordNativeAdClick() {
        val records = getNativeAllRecords()
        records.add(AdRecord(System.currentTimeMillis(), "", "click"))
        saveNativeRecords(records)
    }

    // 记录广告请求
    fun recordNativeAdRequest() {
        val records = getNativeAllRecords()
        records.add(AdRecord(System.currentTimeMillis(), "", "request"))
        saveNativeRecords(records)
    }

    // 记录广告请求
    fun recordAdRequest() {
        val records = getAllRecords()
        records.add(AdRecord(System.currentTimeMillis(), "", "request"))
        saveRecords(records)
    }

    // 检查是否可以展示广告
    fun canShowAd(areaKey: String): Boolean{
        initConfig()
        if(config == null){
            return false
        }
        cleanExpiredRecords()
        val now = System.currentTimeMillis()
        val records = getAllRecords()
        // 1. 检查总广告展示上限
        val totalShowsToday = records.count {
            it.type == "show" && now - it.timestamp < config!!.limited_loadtime_seconds*1000
        }
        val totalClickToday = records.count {
            it.type == "click" && now - it.timestamp < config!!.limited_loadtime_seconds*1000
        }
        val totalRequestToday = records.count {
            it.type == "request" && now - it.timestamp < config!!.limited_loadtime_seconds*1000
        }

        if(totalShowsToday >= config!!.limited||totalClickToday >= config!!.totalClickMaxPerDay||totalRequestToday >= config!!.totalRequestMaxPerDay) {
            AppPrefsUtils.commitBoolean("canLoadAd",false)
            return false
        }
        AppPrefsUtils.commitBoolean("canLoadAd",true)
        val adUnit: AdUnit? = config!!.ad_units.find { it.areakey == areaKey }
        if (adUnit != null) {
            // 2. 检查广告位每小时限制
            val hourAgo = now - 60 * 60 * 1000L
            val positionShowsLastHour = records.count {
                it.type == "show" &&
                        it.areaKey == areaKey &&
                        it.timestamp > hourAgo
            }
            Log.e("AdlimitManager","positionShowsLastHour"+positionShowsLastHour)
            if (positionShowsLastHour >= adUnit.frequency_caps.max_per_hour) {
                return false
            }
            // 3. 检查广告位每日限制（使用可配置的总时长）
            val positionShowsToday = records.count {
                it.type == "show" &&
                        it.areaKey == areaKey &&
                        now - it.timestamp < config!!.limited_loadtime_seconds*1000
            }

            if (positionShowsToday >= adUnit.frequency_caps.max_per_day) {
                return false
            }
            // 4. 检查广告位展示间隔
            val lastShow = records
                .filter { it.type == "show" && it.areaKey == areaKey }
                .maxByOrNull { it.timestamp }
            lastShow?.let {
                if (now - it.timestamp < adUnit.frequency_caps.interval_seconds*1000) {
                    return false
                }
            }
            return shouldPass(adUnit.rate)
        } else {
            return false
        }
    }

    // 检查是否可以展示广告
    fun canShowNativeAd(areaKey: String): Boolean{
        initNativeConfig()
        if(nativeConfig == null){
            return false
        }
        cleanExpiredNativeRecords()
        val now = System.currentTimeMillis()
        val records = getNativeAllRecords()
        // 1. 检查总广告展示上限
        val totalShowsToday = records.count {
            it.type == "show" && now - it.timestamp < nativeConfig!!.limited_loadtime_seconds*1000
        }
        val totalClickToday = records.count {
            it.type == "click" && now - it.timestamp < nativeConfig!!.limited_loadtime_seconds*1000
        }
        val totalRequestToday = records.count {
            it.type == "request" && now - it.timestamp < nativeConfig!!.limited_loadtime_seconds*1000
        }


        if(totalShowsToday >= nativeConfig!!.limited||totalClickToday >= nativeConfig!!.totalClickMaxPerDay||totalRequestToday >= nativeConfig!!.totalRequestMaxPerDay) {
            AppPrefsUtils.commitBoolean("canLoadNativeAd",false)
            return false
        }
        AppPrefsUtils.commitBoolean("canLoadNativeAd",true)
        val adUnit: AdUnit? = nativeConfig!!.ad_units.find { it.areakey == areaKey }
        if (adUnit != null) {
            // 2. 检查广告位每小时限制
            Log.e("AdlimitManager","shouldPass(adUnit.rate)"+shouldPass(adUnit.rate))
            return shouldPass(adUnit.rate)
        } else {
            return false
        }
    }



}






fun shouldPass(passRate: Double): Boolean {
    // 参数验证
    if (passRate <= 0) return false
    if (passRate >= 1) return true
    // 使用 ThreadLocalRandom 提高性能且线程安全
    return ThreadLocalRandom.current().nextDouble() < passRate
}
