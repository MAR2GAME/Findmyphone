package com.findmyphone.gpstracker.bean

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PhoneNumberInfo(
    val originalNumber: String,           // 原始输入号码
    val internationalFormat: String,      // 国际格式，如 "+86 138 1234 5678"
    val countryCode: Int,                 // 国家代码，如 86
    val countryIsoCode: String,           // ISO 国家代码，如 "CN"
    val countryName: String,              // 国家名称，如 "China"
    val city: String,                     // 城市/地区描述，如 "Beijing"
    val timezone: String,                 // 时区，如 "Asia/Shanghai"
    val carrier: String,                  // 原始运营商，如 "China Mobile"
    val isValid: Boolean                  // 是否有效
): Parcelable