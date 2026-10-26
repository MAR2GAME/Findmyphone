package com.findmyphone.gpstracker.ad

import com.findmyphone.gpstracker.utils.AppPrefsUtils


class AdvCheckParams {
    var fromNature: Boolean by AppPrefsUtils.PreferenceDelegate("is_fromNature", false)
    var fromQuantity: Boolean by AppPrefsUtils.PreferenceDelegate("is_fromQuantity", false)
    var isFirstOpen: Boolean by AppPrefsUtils.PreferenceDelegate("isFirstOpen", true)
    var installTime: Long by AppPrefsUtils.PreferenceDelegate("installTime", 0L)
}
object AdvCheckManager {
    val params: AdvCheckParams = AdvCheckParams()
}