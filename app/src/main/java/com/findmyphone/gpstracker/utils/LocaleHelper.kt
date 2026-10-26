package com.findmyphone.gpstracker.utils
import android.R.attr.country
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import com.locshare.locate.util.LocaleHelper.getSavedLocale
import com.locshare.locate.util.LocaleHelper.saveLocale
import java.util.Locale


object LocaleHelper {

    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    private const val KEY_COUNTRY = "selected_country"
    private const val KEY_SCRIPT = "selected_script"

    /**
     * 进程内缓存。
     *
     * 解决两个问题：
     *  1. 部分 ROM 在 Application.attachBaseContext 早期调用 getSharedPreferences
     *     可能拿不到值，导致刚保存的语言读不回来（表现为"设置了没生效"）。
     *  2. 避免每次 Activity 启动都读一次 SP。
     *
     * 注意：只在读到用户保存的语言后写入缓存，系统语言不缓存——
     *      否则用户首次设置语言后，本进程还会继续读旧的系统语言。
     */
    @Volatile
    private var cachedLocale: Locale? = null

    private fun prefs(context: Context): SharedPreferences {
        // attachBaseContext 阶段 applicationContext 仍可能为 null，必须回退到 context
        val ctx = context.applicationContext ?: context
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存用户选择的语言（同步 commit，保证在 killProcess 前一定落盘）。
     * 同时更新进程内缓存，即使 SP 读写异常，本进程内也能拿到正确语言。
     */
    fun saveLocale(context: Context, locale: Locale) {
        cachedLocale = locale

        val script = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            locale.script.orEmpty()
        } else {
            ""
        }
        prefs(context).edit()
            .putString(KEY_LANGUAGE, locale.language)
            .putString(KEY_COUNTRY, locale.country.orEmpty())
            .putString(KEY_SCRIPT, script)
            .commit()
    }

    /**
     * 获取已保存的 Locale，未保存则返回系统首选语言。
     */
    fun getSavedLocale(context: Context): Locale {
        cachedLocale?.let { return it }

        val p = prefs(context)
        val language = p.getString(KEY_LANGUAGE, null)
        if (language.isNullOrEmpty()) return getSystemLocale()

        val country = p.getString(KEY_COUNTRY, "").orEmpty()
        val script = p.getString(KEY_SCRIPT, "").orEmpty()

        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Locale.Builder().apply {
                setLanguage(language)
                if (country.isNotEmpty()) setRegion(country)
                if (script.isNotEmpty()) setScript(script)
            }.build()
        } else {
            @Suppress("DEPRECATION")
            if (country.isNotEmpty()) Locale(language, country) else Locale(language)
        }
        cachedLocale = locale
        return locale
    }

    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val list = LocaleList.getDefault()
            if (list.size() > 0) list[0] else Locale.getDefault()
        } else {
            Locale.getDefault()
        }
    }

    /**
     * 用 createConfigurationContext 构造带目标语言的 Context。
     *
     * 【关键修复 1】Locale.setDefault(locale)
     *   createConfigurationContext 只影响该 Context 的 Resources，
     *   不会修改进程级默认 Locale。AppCompat / Material / DatePicker /
     *   TextUtils / String.format / 三方库等大量使用 Locale.getDefault()，
     *   不设置会导致"部分界面语言变了、部分没变"，这是跨机型差异的头号原因。
     *
     * 【关键修复 2】config.locale 与 config.setLocales 双写
     *   部分定制 ROM（MIUI/EMUI/ColorOS 老版本）与老框架只读 config.locale，
     *   不同步会导致这些机型语言切不过去。
     */
    private fun wrapContext(base: Context, locale: Locale): Context {
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        @Suppress("DEPRECATION")
        config.locale = locale

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    /**
     * 供 MyApp / BaseActivity 的 attachBaseContext 调用。
     * 必须在 attachBaseContext 阶段执行，后续所有资源加载才会用新语言。
     */
    fun setLocaleForApplication(base: Context): Context {
        val locale = getSavedLocale(base)
        return wrapContext(base, locale)
    }

    /**
     * 保存语言（不主动刷新界面、不触发系统重建）。
     *
     * 刻意不调用 LocaleManager.setApplicationLocales()：
     * 我们的 attachBaseContext 方案已经接管语言切换，
     * 再调系统 API 会导致系统强制 recreate / kill 进程，
     * 表现为"点击应用后页面直接关闭"。
     */
    fun setAppLocale(context: Context, locale: Locale) {
        saveLocale(context, locale)
    }

    /**
     * 保存语言并重建当前 Activity。
     * 适用于"原地刷新语言"场景（不跳转、不杀进程）。
     */
    fun setAppLocaleAndRecreate(activity: AppCompatActivity, locale: Locale) {
        saveLocale(activity, locale)
        activity.recreate()
    }

    /**
     * 根据保存的语言，在映射表中匹配到最接近的 key。
     * 匹配优先级：语言+国家 > 仅语言 > 首项。
     *
     * 注意 normalizeLanguageCode：老设备/老 JDK 上 Locale.getDefault().language
     * 可能返回 "in"（旧代码），而 forLanguageTag("id") 返回 "id"（新代码），
     * 不统一会导致默认选中错语言。
     */
    fun getDefaultLanguageKey(context: Context, languageMap: Map<String, Locale>): String {
        val saved = getSavedLocale(context)
        val savedLang = normalizeLanguageCode(saved.language)

        languageMap.entries.firstOrNull {
            normalizeLanguageCode(it.value.language) == savedLang &&
                    it.value.country == saved.country
        }?.let { return it.key }

        languageMap.entries.firstOrNull {
            normalizeLanguageCode(it.value.language) == savedLang
        }?.let { return it.key }

        return languageMap.keys.first()
    }

    /** 统一历史遗留语言代码：in→id、iw→he、ji→yi。 */
    private fun normalizeLanguageCode(code: String): String = when (code) {
        "in" -> "id"
        "iw" -> "he"
        "ji" -> "yi"
        else -> code
    }
}