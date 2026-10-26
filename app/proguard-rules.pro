# ==================== 基础保留（必须） ====================
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, Signature, Exceptions, InnerClasses, EnclosingMethod

# 四大组件
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# 自定义 View 构造方法
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 混淆防重打包类
-keep class com.findmyphone.gpstracker.utils.SafeChecker {*;}
-keepclassmembers class com.findmyphone.gpstracker.utils.SafeChecker {
    native <methods>;
}

# 枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 本地方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# R 文件
-keep class **.R$* {
    <fields>;
}

# ==================== Kotlin ====================
-keep class kotlin.Metadata { *; }

# ==================== 第三方库 ====================

# EventBus
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Gson（最精简正确版）
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# OkHttp
-keep interface okhttp3.Interceptor { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**

# Glide
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** { *; }

# CameraX
-keep class androidx.camera.core.** { *; }
-dontwarn androidx.camera.**

# Lottie
-keep class com.airbnb.lottie.** { *; }

# PhotoView
-keep class com.github.chrisbanes.photoview.** { *; }

# SmartRefreshLayout
-keep class com.scwang.smart.refresh.layout.** { *; }

# BaseRecyclerViewAdapterHelper
-keep class com.chad.library.adapter.** { *; }

# Country Code Picker
-keep class com.hbb20.** { *; }

# libphonenumber
-keep class com.google.i18n.phonenumbers.** { *; }

# ZXing
-keep class com.google.zxing.** { *; }


-keep class com.google.android.gms.ads.** { *; }    # Google 移动广告
-keep class com.google.android.gms.common.** { *; } # Google Play 服务基础规则
# Google 地图/定位（只保留核心，不整包保留）
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.android.gms.**

# ==================== 项目数据 Bean ====================
-keep class com.findmyphone.gpstracker.bean.** { *; }

# ==================== WebView ====================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * extends android.webkit.WebChromeClient { *; }
-keepclassmembers class * extends android.webkit.WebViewClient { *; }
-dontwarn android.webkit.**

# ==================== DataBinding / ViewBinding ====================
-keep class androidx.databinding.** { *; }
-keep class * extends androidx.databinding.DataBinderMapper { *; }
-keep class **.databinding.* { *; }
-keep class **BR { *; }

# ==================== AndroidX Lifecycle ====================
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# ==================== @Keep 全局注解 ====================
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <fields>;
}

# ==================== 忽略警告 ====================
-dontwarn org.apache.**
-dontwarn javax.annotation.**
-dontwarn java.lang.invoke.**
-dontwarn com.google.android.gms.**

# 保留 BuildConfig 类及其所有字段
-keep class com.findmyphone.gpstracker.BuildConfig { *; }

# 如果使用 kotlin，有时还需要
-dontwarn com.findmyphone.gpstracker.BuildConfig

-keep class com.google.android.libraries.places.** { *; }

# --- 广告网络 SDK 规则（AppLovin, Chartboost, Fyber, InMobi, IronSource, Mintegral, Vungle, Unity, Pangle）
-keep class com.google.ads.mediation.** { *; }
-keep class com.applovin.** { *; }           # AppLovin 旗舰功能需保留
-keep class com.chartboost.** { *; }         # Chartboost 混淆规则
-keep class com.fyber.** { *; }              # Fyber 库规则
-keep class com.inmobi.** { *; }             # InMobi 混淆规则
-keep class com.ironsource.** { *; }         # IronSource 混淆规则
-keep class com.mbridge.** { *; }            # Mintegral 混淆规则
-keep class com.vungle.** { *; }             # Vungle 混淆规则
-keep class com.unity3d.** { *; }            # Unity Ads 混淆规则
-keep class com.pangle.** { *; }             # Pangle 广告网络混淆规则

# --- 归因与分析 SDK 规则（Singular, Facebook, Adjust 或其它分析工具）
-keep class com.singular.** { *; }           # Singular SDK 混淆规则[reference:10]
-keep class com.facebook.** { *; }           # Facebook SDK
-keep class * extends java.lang.annotation.Annotation # 保留所有注解，归因 SDK 常用

# --- ThinkingAnalytics 及 TT 相关 SDK 规则
-keep class cn.thinkingdata.** { *; }        # Thinking Analytics 规则[reference:11]
-keep class com.pangle.** { *; }             # TikTok Business 规则[reference:12]

-keep class com.google.android.play.** { *; }       # In-app Update & Integrity[reference:13]
-keep class com.google.android.ump.** { *; }         # User Messaging Platform 用户消息平台




