package com.findmyphone.gpstracker.bean

import android.content.Intent

data class TemporaryNotificationBean (
    var title:String,
    var content:String,
    var button:String,
    var iconRes:Int=0,
    var layoutId:Int=0,
    var bigLayoutId:Int=0,
    var headsUpLayoutId:Int=0,
    var intent: Intent?=null
)