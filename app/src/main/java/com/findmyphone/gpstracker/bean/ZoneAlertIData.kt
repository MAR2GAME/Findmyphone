package com.findmyphone.gpstracker.bean

data class ZoneAlertIData(
    val items: MutableList<ZoneAlertItem>,
    val page: Int,
    val size: Int,
    val total: Int
)

data class ZoneAlertItem(
    val eventType: String,
    val friendDisplayName: String,
    val friendGender: String,
    val historyId: Long,
    val triggeredAt: Long?,
    val zoneName: String,
    val defaultTemplate: Boolean
)