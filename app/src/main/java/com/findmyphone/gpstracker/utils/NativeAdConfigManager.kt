package com.findmyphone.gpstracker.utils

import com.findmyphone.gpstracker.bean.NativeAdClickToHideConfig
import com.google.gson.Gson
import com.ironsource.adqualitysdk.sdk.i.it

import kotlin.collections.any
import kotlin.collections.firstOrNull
import kotlin.jvm.java
import kotlin.text.isBlank

object NativeAdConfigManager {

    private var config: NativeAdClickToHideConfig? = null

    /**
     * 初始化配置（建议在 Application.onCreate 中调用）
     * 也可以改成懒加载，在第一次 getConfig() 时自动初始化
     */
    fun init() {
        // 从你的持久化存储里取出 JSON 字符串
        val jsonStr = AppPrefsUtils.getString("nativead_click_to_hideconfig", "")
        config = parseConfig(jsonStr)
    }

    /**
     * 获取当前配置，若未初始化则返回默认值或空
     */
    fun getConfig(): NativeAdClickToHideConfig {
        return config ?: NativeAdClickToHideConfig(emptyList())
    }

    /**
     * 根据 areakey 查找对应 clickSize，方便页面直接调用
     */
    fun getClickSize(areakey: String): Int {
        return getConfig().configs
            .firstOrNull { it.areakey == areakey }
            ?.clickSize ?: 0  // 默认 0 或按需求调整
    }

    /**
     * 判断某个 areakey 是否存在于配置列表中
     */
    fun containsAreaKey(areakey: String): Boolean {
        return getConfig().configs.any { it.areakey == areakey }
    }

    /**
     * 可选：当 remoteConfig 更新后重新加载
     */
    fun reload() {
        init()
    }

    private fun parseConfig(jsonStr: String): NativeAdClickToHideConfig {
        if (jsonStr.isBlank()) return NativeAdClickToHideConfig(emptyList())
        return try {
            val gson = Gson()
            gson.fromJson(jsonStr, NativeAdClickToHideConfig::class.java)
        } catch (e: Exception) {
            // 解析失败时返回默认空配置，避免崩溃
            NativeAdClickToHideConfig(emptyList())
        }
    }
}