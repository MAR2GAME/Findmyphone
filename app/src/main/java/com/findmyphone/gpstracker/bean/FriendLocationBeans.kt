package com.findmyphone.gpstracker.bean

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

class FriendLocationBeans(
    val items: MutableList<FriendLocationBeansItem>,
    val total: Int,
    var page:Int,
    var size:Int

)
@Parcelize
data class FriendLocationBeansItem(
    val displayName: String,
    val friendUserId: Long,
    val gender: String,
    val position: Position?,
    val friendSecurityCode: String
): Parcelable
@Parcelize
data class Position(
    val lastUpdatedAt: Long,
    val latitude: Double,
    val longitude: Double
):Parcelable