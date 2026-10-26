import groovy.json.JsonSlurper
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY") ?: ""

fun advertiseStringProperty(
    propertyName: String,
    environmentName: String,
    defaultValue: String = "",
): String = localProperties.getProperty(propertyName)
    ?: providers.gradleProperty(propertyName).orNull
    ?: providers.environmentVariable(environmentName).orNull
    ?: defaultValue

fun advertiseBooleanProperty(
    propertyName: String,
    environmentName: String,
    defaultValue: Boolean,
): Boolean = advertiseStringProperty(propertyName, environmentName, defaultValue.toString())
    .equals("true", ignoreCase = true)

fun advertiseLongProperty(
    propertyName: String,
    environmentName: String,
    defaultValue: Long,
): Long = advertiseStringProperty(propertyName, environmentName, defaultValue.toString())
    .toLongOrNull() ?: defaultValue

val remoteConfigKeyFile = rootProject.file("key/remote_config_key_20260722_124844.json")
val remoteConfigKeyPayload: Map<String, Any?> by lazy {
    if (!remoteConfigKeyFile.isFile) {
        return@lazy emptyMap()
    }
    val parsed = try {
        JsonSlurper().parse(remoteConfigKeyFile)
    } catch (cause: Exception) {
        throw GradleException("Unable to parse the local Remote Config key file.", cause)
    }
    if (parsed !is Map<*, *>) {
        throw GradleException("The local Remote Config key file must contain a JSON object.")
    }
    parsed.entries.associate { (key, value) -> key.toString() to value }
}

fun remoteConfigEncryptionProperty(
    propertyName: String,
    environmentName: String,
    keyFileField: String,
): String {
    providers.gradleProperty(propertyName).orNull?.takeIf { it.isNotBlank() }?.let { return it }
    providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }?.let { return it }
    val keyFileValue = remoteConfigKeyPayload[keyFileField] as? String
    if (remoteConfigKeyFile.isFile && keyFileValue.isNullOrBlank()) {
        throw GradleException("The local Remote Config key file is missing a non-empty '$keyFileField' value.")
    }
    return keyFileValue.orEmpty()
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val advertiseAdmobAppId = advertiseStringProperty(
    "adv.admob.appId",
    "ADV_ADMOB_APP_ID",
    "ca-app-pub-3643549411810853~3318290879",
)
val advertiseFacebookAppId = advertiseStringProperty(
    "adv.facebook.appId",
    "ADV_FACEBOOK_APP_ID",
)
val advertiseFacebookClientToken = advertiseStringProperty(
    "adv.facebook.clientToken",
    "ADV_FACEBOOK_CLIENT_TOKEN",
)

android {
    namespace = "com.findmyphone.gpstracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.findmyphone.gpstracker"
        minSdk = 29
        targetSdk = 36
        versionCode = 22
        versionName = "1.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MAPS_API_KEY", "\"${mapsApiKey}\"")

        fun advertiseStringField(
            name: String,
            property: String,
            environment: String,
            default: String = "",
        ) {
            buildConfigField(
                "String",
                name,
                advertiseStringProperty(property, environment, default).asBuildConfigString(),
            )
        }

        fun remoteConfigEncryptionStringField(
            name: String,
            propertyName: String,
            environmentName: String,
            keyFileField: String,
        ) {
            buildConfigField(
                "String",
                name,
                remoteConfigEncryptionProperty(
                    propertyName,
                    environmentName,
                    keyFileField,
                ).asBuildConfigString(),
            )
        }

        fun advertiseBooleanField(
            name: String,
            property: String,
            environment: String,
            default: Boolean,
        ) {
            buildConfigField(
                "boolean",
                name,
                advertiseBooleanProperty(property, environment, default).toString(),
            )
        }

        fun advertiseLongField(
            name: String,
            property: String,
            environment: String,
            default: Long,
        ) {
            buildConfigField(
                "long",
                name,
                "${advertiseLongProperty(property, environment, default)}L",
            )
        }

        advertiseBooleanField("ADV_REMOTE_CONFIG_ENABLED", "adv.remoteConfig.enabled", "ADV_REMOTE_CONFIG_ENABLED", true)
        remoteConfigEncryptionStringField(
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY",
            "adv.remoteConfig.encryptionKey",
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY",
            "encryptionKeyBase64",
        )
        remoteConfigEncryptionStringField(
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID",
            "adv.remoteConfig.encryptionKeyId",
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID",
            "keyId",
        )
        advertiseBooleanField("ADV_THINKING_ENABLED", "adv.thinking.enabled", "ADV_THINKING_ENABLED", true)
        advertiseStringField("ADV_THINKING_APP_KEY", "adv.thinking.appKey", "ADV_THINKING_APP_KEY", "67056903b4b546baa956431440a054ea")
        advertiseStringField("ADV_THINKING_SERVER_URL", "adv.thinking.serverUrl", "ADV_THINKING_SERVER_URL", "https://mar2.top")
        advertiseBooleanField("ADV_SINGULAR_ENABLED", "adv.singular.enabled", "ADV_SINGULAR_ENABLED", true)
        advertiseStringField("ADV_SINGULAR_API_KEY", "adv.singular.apiKey", "ADV_SINGULAR_API_KEY", "mar2game_f7b9272a")
        advertiseStringField("ADV_SINGULAR_SECRET", "adv.singular.secret", "ADV_SINGULAR_SECRET", "72b3df2ee5d0a64a6c404ce01937c3d6")
        advertiseStringField("ADV_ADMOB_APP_ID", "adv.admob.appId", "ADV_ADMOB_APP_ID", "ca-app-pub-3643549411810853~3318290879")
        advertiseStringField("ADV_ADMOB_INTERSTITIAL_ID", "adv.admob.interstitialId", "ADV_ADMOB_INTERSTITIAL_ID", "ca-app-pub-3643549411810853/7121804517")
        advertiseStringField("ADV_ADMOB_NATIVE_ID", "adv.admob.nativeId", "ADV_ADMOB_NATIVE_ID")
        advertiseStringField("ADV_ADMOB_OPEN_ID", "adv.admob.openId", "ADV_ADMOB_OPEN_ID", "ca-app-pub-3643549411810853/4615599677")
        advertiseStringField("ADV_ADMOB_REWARDED_ID", "adv.admob.rewardedId", "ADV_ADMOB_REWARDED_ID")
        advertiseBooleanField("ADV_SAFE_ENABLED", "adv.safe.enabled", "ADV_SAFE_ENABLED", false)
        advertiseBooleanField("ADV_PLAY_INTEGRITY_ENABLED", "adv.playIntegrity.enabled", "ADV_PLAY_INTEGRITY_ENABLED", false)
        advertiseLongField("ADV_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", "adv.playIntegrity.cloudProjectNumber", "ADV_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", 0L)
        advertiseBooleanField("ADV_FACEBOOK_ENABLED", "adv.facebook.enabled", "ADV_FACEBOOK_ENABLED", false)
        advertiseStringField("ADV_FACEBOOK_APP_ID", "adv.facebook.appId", "ADV_FACEBOOK_APP_ID")
        advertiseStringField("ADV_FACEBOOK_CLIENT_TOKEN", "adv.facebook.clientToken", "ADV_FACEBOOK_CLIENT_TOKEN")
        advertiseBooleanField("ADV_TIKTOK_ENABLED", "adv.tiktok.enabled", "ADV_TIKTOK_ENABLED", false)
        advertiseStringField("ADV_TIKTOK_ACCESS_TOKEN", "adv.tiktok.accessToken", "ADV_TIKTOK_ACCESS_TOKEN")
        advertiseStringField("ADV_TIKTOK_TT_APP_ID", "adv.tiktok.ttAppId", "ADV_TIKTOK_TT_APP_ID")
        manifestPlaceholders["advAdmobAppId"] = advertiseAdmobAppId
        manifestPlaceholders["advFacebookAppId"] = advertiseFacebookAppId
        manifestPlaceholders["advFacebookClientToken"] = advertiseFacebookClientToken
   //      NDK 配置
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++11 -O2 -fvisibility=hidden")
                abiFilters.addAll(listOf("arm64-v8a"))
                arguments("-DCMAKE_BUILD_TYPE=Release")
            }
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    signingConfigs {
        create("release") {
            storeFile = file("map_pulse.jks")
            storePassword = "map2026"
            keyAlias = "mapkey"
            keyPassword = "mapkey2026"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField(
                "boolean",
                "ADV_ADMOB_ENABLED",
                advertiseBooleanProperty("adv.admob.enabled", "ADV_ADMOB_ENABLED", true).toString(),
            )
            buildConfigField("String", "ADV_DEFAULT_TOPIC", "\"all\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = true
            buildConfigField(
                "boolean",
                "ADV_ADMOB_ENABLED",
                advertiseBooleanProperty("adv.admob.debugEnabled", "ADV_ADMOB_DEBUG_ENABLED", true).toString(),
            )
            buildConfigField("String", "ADV_DEFAULT_TOPIC", "\"debug-all\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.integrity)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.runtime.saved.instance.state)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.play.services.maps)
    implementation(libs.glide)
    annotationProcessor(libs.compiler)
    implementation(libs.baserecyclerviewadapterhelper)
    api(libs.androidx.lifecycle.extensions)
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.androidx.lifecycle.livedata)
    implementation(libs.eventbus)
    implementation(libs.androidx.databinding.runtime)
    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    implementation(libs.lottie)
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)
    implementation(libs.material.v160)
    implementation(libs.gson)
    // 添加这一行
    implementation(libs.kotlinx.coroutines.play.services)
    // 国家区号库
    implementation(libs.ccp)
    implementation(libs.libphonenumber)
    // 地理编码（获取城市/地区）
    implementation(libs.geocoder)
// 运营商映射（获取运营商）
    implementation(libs.carrier)
    implementation(libs.photoview)
    implementation(libs.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.places)
    implementation(project(":pickerview"))
    // 核心库 (kernel)
    implementation(libs.refresh.layout.kernel)
// 经典刷新头部
    implementation(libs.refresh.header.classics)
// 经典加载更多底部
    implementation(libs.refresh.footer.classics)
    implementation(platform(libs.firebase.bom))
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.config)
    implementation(libs.google.firebase.crashlytics.ndk)
    implementation(libs.google.firebase.messaging)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)
    implementation(libs.thinkinganalyticssdk)
    implementation(libs.play.services.ads)
    implementation(libs.singular.sdk)
    implementation(libs.play.services.base)
    implementation(libs.applovin)
    implementation(libs.chartboost)
    implementation(libs.fyber)
    implementation(libs.inmobi)
    implementation(libs.ironsource)
    implementation(libs.vungle)
    implementation(libs.facebook)
    implementation(libs.mintegral)
    implementation(libs.pangle)
    implementation(libs.unity.ads)
    implementation(libs.unity)
    implementation(libs.facebook.android.sdk)
    implementation(libs.user.messaging.platform)
    implementation(libs.integrity)
    implementation(libs.shimmer)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.common.java8)
//to get Google install referrer
    implementation(libs.installreferrer)
    implementation(libs.play.services.ads.identifier)
    implementation("com.pdffox:advertise:1.0.12")
    implementation("cn.thinkingdata.android:ThinkingAnalyticsSDK:3.4.4")



}
