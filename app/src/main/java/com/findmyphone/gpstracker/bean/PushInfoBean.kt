package com.findmyphone.gpstracker.bean

data class PushInfoBean(
    val each_trigger_sent: Int,
    val has_sound_alert: Boolean,
    val has_vibration: Boolean,
    val max24H: Int,
    val NMax:Int,
    val triggers: List<Trigger>
)

data class Trigger(
    val filter: List<ScreenOnFilter>?=null,
    val id: Int,
    val interval_second: Int=0,
    val offset_second: Int=0
)

data class ScreenOnFilter(
    val interval_second: Int,
    val offset_second: Int,
    val screen_on_second: Int
)