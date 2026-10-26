package com.findmyphone.gpstracker.config


import com.findmyphone.gpstracker.BuildConfig
import com.findmyphone.gpstracker.ui.AddFriendActivity
import com.findmyphone.gpstracker.ui.DeleteFriendActivity
import com.findmyphone.gpstracker.ui.EditFriendActivity
import com.findmyphone.gpstracker.ui.FootprintsActivity
import com.findmyphone.gpstracker.ui.GuideActivity
import com.findmyphone.gpstracker.ui.LiveLocationActivity
import com.findmyphone.gpstracker.ui.LocationsListActivity
import com.findmyphone.gpstracker.ui.LoginActivity
import com.findmyphone.gpstracker.ui.MainActivity
import com.findmyphone.gpstracker.ui.NativeAdActivity
import com.findmyphone.gpstracker.ui.PhoneInfoActivity
import com.findmyphone.gpstracker.ui.ScannerActivity
import com.findmyphone.gpstracker.ui.SearchPhoneActivity
import com.findmyphone.gpstracker.ui.SetLanguageActivity
import com.findmyphone.gpstracker.ui.ShareMyCodeActivity
import com.findmyphone.gpstracker.ui.ShowTrainActivity
import com.findmyphone.gpstracker.ui.SosActivity
import com.findmyphone.gpstracker.ui.SplashActivity
import com.findmyphone.gpstracker.ui.WebViewActivity
import com.findmyphone.gpstracker.ui.ZoneAlertActivity
import com.findmyphone.gpstracker.ui.ZoneDetailsActivity
import com.findmyphone.gpstracker.ui.ZoneListActivity
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import kotlin.jvm.java

object AppConfig {
    const val packageName = "com.findmyphone.gpstracker"
    const val START_TIME = "config_start_time"
    const val TERMS_URL = "https://sites.google.com/view/findmyphone-terms-conditions/home"
    const val PRIVACY_URL = "https://sites.google.com/view/findmyphone-privacy-policy/home"
    const val ADVHost = "https://api.cigars-of-cuba.top"


    const val ACTION_CUSTOM_BROADCAST = "com.findmyphone.gpstracker.action.CUSTOM_BROADCAST"

    const val TRAIN_URL1="https://loc-share.oss-us-west-1.aliyuncs.com/atlanta-metro-marta-map-official-2025.jpg"
    const val TRAIN_URL2="https://loc-share.oss-us-west-1.aliyuncs.com/baltimore-metro-subwaylink-map-official.jpg"
    const val TRAIN_URL3="https://loc-share.oss-us-west-1.aliyuncs.com/boston-mbta-subway-map-official-2025-08-25.jpg"
    const val TRAIN_URL4="https://loc-share.oss-us-west-1.aliyuncs.com/chicago-cta-l-system-map-official.jpg"
    const val TRAIN_URL5="https://loc-share.oss-us-west-1.aliyuncs.com/cleveland-rta-rapid-transit-map-official.jpg"
    const val TRAIN_URL6="https://loc-share.oss-us-west-1.aliyuncs.com/hawaii-honolulu-skyline-route-map-official-2026-03.jpg"
    const val TRAIN_URL7="https://loc-share.oss-us-west-1.aliyuncs.com/los-angeles-metro-rail-busway-map-official-2026-05.jpg"
    const val TRAIN_URL8="https://loc-share.oss-us-west-1.aliyuncs.com/miami-dade-metrorail-map-official-page-001.jpg"
    const val TRAIN_URL9="https://loc-share.oss-us-west-1.aliyuncs.com/new-jersey-nj-transit-rail-system-map-official-2020.jpg"
    const val TRAIN_URL10="https://loc-share.oss-us-west-1.aliyuncs.com/new-york-mta-subway-diagram-official-2026-05.jpg"
    const val TRAIN_URL11="https://loc-share.oss-us-west-1.aliyuncs.com/philadelphia-septa-metro-system-map-official.jpg"
    const val TRAIN_URL12="https://loc-share.oss-us-west-1.aliyuncs.com/san-francisco-bart-system-map-effective-2025-01.jpg"
    const val TRAIN_URL13="https://loc-share.oss-us-west-1.aliyuncs.com/washington-wmata-rail-system-map-official.jpg"





    const val THINKING_APPID = "67056903b4b546baa956431440a054ea"
    const val THINKING_URL = "https://mar2.top"
    const val IS_SET_USER="is_set_user"

   const val BASE_URL="https://foundphone.top"
  // const val BASE_URL="http://192.168.110.35:10123"
    const val TOKEN="token"
    var isDebug = BuildConfig.DEBUG
    const val NICK_NAME="nick_name"
    const val USER_CODE="user_code"
    const val MEMBER_STATE="member_state"
    const val DEVICE_CODE="device_code"
    val ParseTokenUrl: String
        get() = "$ADVHost/parseToken"
    val IPInfoV2Url: String
        get() = "$ADVHost/getIpInfoV2"


    const val Singular_Api_Key = "mar2game_f7b9272a"
    const val Singular_Secret = "72b3df2ee5d0a64a6c404ce01937c3d6"


    var notShowNativeAdClazz: Set<Class<*>> = setOf(
        SetLanguageActivity::class.java,
        GuideActivity::class.java,
        SplashActivity::class.java,
        SosActivity::class.java,
        WebViewActivity::class.java,
        NativeAdActivity::class.java
    )

    var showAdClazz: Set<Class<*>> = setOf(
        SetLanguageActivity::class.java,
        GuideActivity::class.java,
        LoginActivity::class.java,
        MainActivity::class.java,
        SearchPhoneActivity::class.java,
        PhoneInfoActivity::class.java,
        SosActivity::class.java,
        AddFriendActivity::class.java,
        ScannerActivity::class.java,
        DeleteFriendActivity::class.java,
        EditFriendActivity::class.java,
        ZoneListActivity::class.java,
        ZoneDetailsActivity::class.java,
        LocationsListActivity::class.java,
        ZoneAlertActivity::class.java,
        ShareMyCodeActivity::class.java,
        FootprintsActivity::class.java,
        ShowTrainActivity::class.java,
        LiveLocationActivity::class.java
    )

}
