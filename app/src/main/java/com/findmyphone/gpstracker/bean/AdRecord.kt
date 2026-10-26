package com.findmyphone.gpstracker.bean

data class AdRecord(
    val timestamp: Long,
    val areaKey: String = "", // 广告位标识
    val type: String // "show" 或 "click" 或 "request"
)