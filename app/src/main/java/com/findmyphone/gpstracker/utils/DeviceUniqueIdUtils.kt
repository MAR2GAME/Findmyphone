package com.findmyphone.gpstracker.utils
import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.findmyphone.gpstracker.base.MyApplication
import java.util.Locale
import java.util.UUID
object DeviceUniqueIdUtils {
    // 已知部分旧机型可能返回的无效 Android ID 值
    private val INVALID_ANDROID_IDS = setOf(
        "9774d56d682e549c",   // 早期系统 Bug 固定值
        "0000000000000000",
        "0123456789abcdef"
    )
    // Android ID 的合法长度（通常为 16 位十六进制字符串）
    private const val VALID_ANDROID_ID_LENGTH = 16

    // 预编译正则，避免重复创建
    private val VALID_ANDROID_ID_REGEX = Regex("^[0-9a-fA-F]{$VALID_ANDROID_ID_LENGTH}$")

    /**
     * 获取设备唯一标识符
     * 优先使用 Android ID，若无效则返回持久化的 UUID
     * @return 设备唯一标识符，永不为空
     */
    fun getDeviceUniqueId(): String {
        // 防御性获取 Application 实例，避免过早调用导致空指针
        val context = MyApplication.instance
            ?: throw IllegalStateException("MyApplication has not been initialized yet")
        val androidId = getAndroidId(context)
        return if (isValidAndroidId(androidId)) {
            androidId
        } else {
            getOrCreateUUID()
        }
    }

    /**
     * 获取或生成 UUID（保存在 SharedPreferences 中）
     * 使用同步锁保证多线程安全
     * @return UUID 字符串，永不为空
     */
    @Synchronized
    private fun getOrCreateUUID(): String {
        // 使用 takeIf 避免空断言，代码更优雅
        return AppPrefsUtils.getString("device_uuid")
            ?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString()
                .also { AppPrefsUtils.commitString("device_uuid", it) } // 注：建议使用 apply() 异步提交
    }

    /**
     * 判断 Android ID 是否有效
     * @param id 待检查的 Android ID
     * @return true 表示有效，false 表示无效
     */
    private fun isValidAndroidId(id: String): Boolean {
        if (id.isEmpty()) return false
        // 检查长度是否为16位
        if (id.length != VALID_ANDROID_ID_LENGTH) return false
        // 检查是否全部为十六进制字符（使用预编译正则）
        if (!id.matches(VALID_ANDROID_ID_REGEX)) return false
        // 检查是否在黑名单中（统一转小写比较）
        return !INVALID_ANDROID_IDS.contains(id.lowercase(Locale.ROOT))
    }

    /**
     * 获取 Android ID
     * @param context 上下文
     * @return Android ID，若获取失败返回空字符串
     */
    @SuppressLint("HardwareIds")
    private fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""
    }
}