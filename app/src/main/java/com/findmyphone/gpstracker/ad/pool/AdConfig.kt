package com.findmyphone.gpstracker.ad.pool

import com.google.gson.Gson
import org.json.JSONObject

object AdConfig {
    var adload_cache_time: Long = 60 * 60 * 1000
    var adload_retry_num: Int = 1
    var adload_max_time: Long = 4 * 1000
    var adload_poolsize_native: Int = 1

    const val LOAD_TIME_OPEN_APP = "open_app"
    const val LOAD_TIME_PLAY_FINISH = "play_finish"
    const val LOAD_TIME_ENTER_BACKGROUND = "enter_background"
    const val LOAD_TIME_RECEIVE_NOTIFICATION = "receive_notification"
    const val ENTER_FEATURES = "enter_features"

    private var nativeTriggers: Map<String, Boolean> = mapOf(
        LOAD_TIME_OPEN_APP to true,
        LOAD_TIME_PLAY_FINISH to true,
        LOAD_TIME_ENTER_BACKGROUND to false,
        LOAD_TIME_RECEIVE_NOTIFICATION to false,
        ENTER_FEATURES to false,
    )

    fun updateConfigFromJson(json: String) {
        runCatching {
            val config = JSONObject(json)
            adload_cache_time =
                config.optLong("adload_cache_time", adload_cache_time / 1000) * 1000
            adload_retry_num = config.optInt("adload_retry_num", adload_retry_num)
            adload_max_time =
                config.optLong("adload_max_time", adload_max_time / 1000) * 1000
            adload_poolsize_native =
                config.optInt("adload_poolsize_native", adload_poolsize_native)
            nativeTriggers = config.optJSONObject("adload_trigger_timing_native")?.let {
                @Suppress("UNCHECKED_CAST")
                Gson().fromJson(it.toString(), Map::class.java) as Map<String, Boolean>
            } ?: nativeTriggers
        }
    }

    fun canLoadNative(loadTimeKey: String): Boolean = nativeTriggers[loadTimeKey] ?: false
}
