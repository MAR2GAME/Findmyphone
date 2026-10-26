package com.findmyphone.gpstracker.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.ump.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

object UmpConsentManager {
    private const val TAG = "UmpConsentManager"
    private const val PREFS_NAME = "ump_consent_prefs"
    private const val KEY_FINISHED = "consent_finished"
    private const val KEY_STATUS = "consent_status"
    private const val KEY_LAST_UPDATE_TIME = "last_update_time"
    private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours

    var consentRequiredAgainListener: (() -> Unit)? = null

    private lateinit var appScope: CoroutineScope
    private val mutex = Mutex()
    private var application: Application? = null

    enum class Status {
        UNKNOWN, REQUIRED, NOT_REQUIRED, OBTAINED
    }

    fun init(app: Application) {
        application = app
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    private fun requireApp(): Application {
        return application ?: throw IllegalStateException(
            "UmpConsentManager not initialized. Call init() in Application.onCreate()"
        )
    }

    private fun prefs(): SharedPreferences {
        return requireApp().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 确保同意流程完成。
     * @param networkTimeoutMs 网络请求部分的超时时间，超时后若无需展示表单则继续；若需表单，表单永不超时。
     */
    suspend fun ensureConsent(
        activity: Activity,
        underAgeOfConsent: Boolean = false,
        networkTimeoutMs: Long = 10_000L,
        onWillShowForm: (() -> Unit)? = null
    ): Status = mutex.withLock {
        val prefs = prefs()

        // 1. 已同意且缓存未过期 → 直接返回
        if (prefs.getBoolean(KEY_FINISHED, false)) {
            val lastUpdate = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)
            if (System.currentTimeMillis() - lastUpdate <= CACHE_MAX_AGE_MS) {
                val cachedStatus = getCachedStatus(prefs) ?: Status.UNKNOWN
                Log.d(TAG, "Using cached status: $cachedStatus")
                return cachedStatus
            }
            // 过期：清除标记，重新走流程
            Log.d(TAG, "Cache expired, forcing full consent flow")
            prefs.edit().putBoolean(KEY_FINISHED, false).apply()
        }

        // 2. 执行完整同意流程（网络部分可超时，表单部分不可超时）
        val status = executeFullConsentFlow(activity, underAgeOfConsent, networkTimeoutMs, onWillShowForm)

        // 3. 保存结果（仅当状态明确时）
        if (status != Status.REQUIRED && status != Status.UNKNOWN) {
            prefs.edit()
                .putBoolean(KEY_FINISHED, true)
                .putString(KEY_STATUS, status.name)
                .putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
                .apply()
        }

        return status
    }

    suspend fun resetConsent(
        activity: Activity,
        underAgeOfConsent: Boolean = false,
        onWillShowForm: (() -> Unit)? = null
    ) = mutex.withLock {
        try {
            val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
            consentInfo.reset()
        } catch (e: Exception) {
            Log.e(TAG, "reset failed: ${e.message}", e)
        }
        prefs().edit().clear().apply()

        val status = executeFullConsentFlow(activity, underAgeOfConsent, 10_000L, onWillShowForm)
        if (status != Status.REQUIRED && status != Status.UNKNOWN) {
            prefs().edit()
                .putBoolean(KEY_FINISHED, true)
                .putString(KEY_STATUS, status.name)
                .putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
                .apply()
        }
    }

    fun canRequestAds(): Boolean {
        val app = requireApp()
        return UserMessagingPlatform.getConsentInformation(app).canRequestAds()
    }

    fun getCurrentStatus(): Status {
        val app = requireApp()
        return mapStatus(UserMessagingPlatform.getConsentInformation(app).consentStatus)
    }

    // ===================== 私有方法 =====================

    private suspend fun executeFullConsentFlow(
        activity: Activity,
        underAgeOfConsent: Boolean,
        networkTimeoutMs: Long,
        onWillShowForm: (() -> Unit)?
    ): Status {
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        // 1. 网络请求更新，带超时保护
        var networkSuccess = false
        try {
            withTimeout(networkTimeoutMs) {
                requestConsentInfoUpdate(activity, consentInfo, underAgeOfConsent)
            }
            networkSuccess = true
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Consent info update timed out after ${networkTimeoutMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Consent info update failed: ${e.message}", e)
        }

        val status = mapStatus(consentInfo.consentStatus)

        // 2. 如果需要，展示表单（必须等待用户操作，永不超时）
        if (consentInfo.isConsentFormAvailable && status == Status.REQUIRED) {
            try {
                onWillShowForm?.invoke()
                loadAndShowConsentForm(activity)
                return mapStatus(consentInfo.consentStatus)
            } catch (e: Exception) {
                Log.e(TAG, "Form display failed: ${e.message}", e)
            }
        }

        return status
    }

    private fun getCachedStatus(prefs: SharedPreferences): Status? {
        val name = prefs.getString(KEY_STATUS, null) ?: return null
        return try { Status.valueOf(name) } catch (e: Exception) { null }
    }

    private fun mapStatus(umpStatus: Int): Status = when (umpStatus) {
        ConsentInformation.ConsentStatus.REQUIRED -> Status.REQUIRED
        ConsentInformation.ConsentStatus.OBTAINED -> Status.OBTAINED
        ConsentInformation.ConsentStatus.NOT_REQUIRED -> Status.NOT_REQUIRED
        else -> Status.UNKNOWN
    }

    // ===================== UMP 回调协程化 =====================

    private suspend fun requestConsentInfoUpdate(
        activity: Activity,
        consentInfo: ConsentInformation,
        underAgeOfConsent: Boolean
    ) = suspendCancellableCoroutine<Unit> { cont ->
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(underAgeOfConsent)
            .build()
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            { if (cont.isActive) cont.resume(Unit) },
            { error ->
                Log.w(TAG, "UMP info update error: ${error.message}")
                if (cont.isActive) cont.resume(Unit)
            }
        )
    }

    private suspend fun loadAndShowConsentForm(activity: Activity) =
        suspendCancellableCoroutine<Unit> { cont ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                activity,
                object : ConsentForm.OnConsentFormDismissedListener {
                    override fun onConsentFormDismissed(formError: FormError?) {
                        if (formError != null) {
                            Log.e(TAG, "Form error: ${formError.message} (code: ${formError.errorCode})")
                        }
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
            )
        }
}