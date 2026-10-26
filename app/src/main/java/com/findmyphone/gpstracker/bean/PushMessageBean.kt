package com.findmyphone.gpstracker.bean

data class PushMessageBean
    (
    val Id:String,
    val AppName:String,
    val AppPackage:String,
    val Policy:Int,
    val NoticeId:String,
    val Title:String,
    val Content:String,
    val Button:String,
    val Icon:String?,
    val Img:String?,
    val Languages:String,
    val Route:String
)

data class LanguagesBean(
    val keys:List<LanguagesItemBean>
)
data class LanguagesItemBean(
    var language:String,
    var title:String,
    var content:String,
    var button:String,
    var img:String?,
)