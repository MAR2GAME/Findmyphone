package com.findmyphone.gpstracker.bean

sealed class SplashEvent {
    /** 跳转主页 */
    object GoToMain : SplashEvent()

    /** 跳转语言设置页 */
    object GoToSetLanguage : SplashEvent()

    /** 打开 WebView 显示指定 URL（隐私政策、服务条款） */
    data class OpenWebView(val url: String) : SplashEvent()
}