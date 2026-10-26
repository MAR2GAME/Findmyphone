package com.findmyphone.gpstracker.bean

data class UserInfo(
    var membershipStatus: String?,
    val nickname: String,
    val securityCode: String,
    val userId: Int
)
