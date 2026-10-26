package com.findmyphone.gpstracker.bean

data class AdLimitConfig(
    val ad_units: List<AdUnit>,
    val limited: Int,
    val limited_loadtime_seconds: Long,
    val totalClickMaxPerDay: Int,
    val totalRequestMaxPerDay: Int
)

data class AdUnit(
    val ad_format: String,
    val areakey: String,
    val frequency_caps: FrequencyCaps,
    val rate: Double
)

data class FrequencyCaps(
    val interval_seconds: Long,
    val max_per_day: Int,
    val max_per_hour: Int
)