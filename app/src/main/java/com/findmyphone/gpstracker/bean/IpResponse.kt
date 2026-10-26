package com.findmyphone.gpstracker.bean

import kotlinx.serialization.Serializable


data class IpResponse(
    val Ip: String,
    val IpLocation: String,
    val location_info: String  // 这是一个 JSON 字符串
)


data class LocationInfo(
    val ip: String,
    val country_code: String,
    var asn: String?,           // ASN 编号（字符串）
    val `as`: String,          // AS 名称
    var isp: String?,           // ISP 名称
    // ... 其他字段根据需要添加
)
