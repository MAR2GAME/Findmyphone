package com.findmyphone.gpstracker.bean

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

data class ZoneListBean(
    val items: MutableList<ZoneItem>,
    val total: Int,
    var page:Int,
    var size:Int

)
@Parcelize
data class ZoneItem(
    var centerLatitude: Double?,
    var centerLongitude: Double?,
    var defaultTemplate: Boolean,
    var radiusMeters: Int?,
    val zoneId: Long?,
    var zoneName: String,
    var onEnter: Boolean,
    var onLeave: Boolean,
    var templateName: String?
) : Parcelable
