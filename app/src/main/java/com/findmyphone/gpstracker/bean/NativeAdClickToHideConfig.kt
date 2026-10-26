package com.findmyphone.gpstracker.bean

data class NativeAdClickToHideConfig(val configs: List<NativeAdClickToHideBean>)

data class  NativeAdClickToHideBean(var areakey:String,var clickSize:Int)