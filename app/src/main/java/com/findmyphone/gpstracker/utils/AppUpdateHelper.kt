package com.findmyphone.gpstracker.utils



import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import com.findmyphone.gpstracker.R

import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.isImmediateUpdateAllowed
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


/**
 * 应用内更新助手（生产级优化版）
 *
 * - 自动管理 Google Play 应用内更新 (Immediate/Flexible)
 * - 安装状态监听、下载完成主动提示
 * - 资源安全释放，无内存泄漏
 *
 * **使用建议**：为每个 Activity 创建独立实例，在 `onResume` 中绑定当前 Activity，
 * 在 `onPause` 中解绑，在 `onDestroy` 中调用 [cleanup] 释放资源。
 *
 * @param context 上下文，内部自动使用 [ApplicationContext] 避免内存泄漏
 * @param coroutineScope 生命周期感知的协程作用域（如 lifecycleScope、viewModelScope）
 * @param updateRequestCode 更新请求码，默认 [UPDATE_REQUEST_CODE]
 * @param snackbarActionText Snackbar 按钮文字（灵活更新完成时显示）
 * @param snackbarMessageText Snackbar 提示文字
 */
class AppUpdateHelper(
    context: Context,
    private val coroutineScope: CoroutineScope,
    private val updateRequestCode: Int = UPDATE_REQUEST_CODE,
    private val snackbarActionText: String? = null,
    private val snackbarMessageText: String? = null
) {

    private val appContext: Context = context.applicationContext
    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(appContext)
    }

    // 监听器注册状态 (幂等)
    private val isListenerRegistered = AtomicBoolean(false)

    // 当前前台 Activity 的弱引用，用于下载完成时主动弹出 Snackbar
    private var currentActivityRef: WeakReference<Activity>? = null

    // 防止同一个 Activity 重复展示 Snackbar
    private val snackbarShowingMap = Collections.synchronizedMap(WeakHashMap<Activity, AtomicBoolean>())

    // 内部协程管理
    private val internalJob = Job()
    private val internalScope = CoroutineScope(Dispatchers.Main + internalJob)

    // 安装状态流（共享，全局只注册一次监听器）
    private val installStateFlow: Flow<InstallState> by lazy {
        callbackFlow {
            val listener = InstallStateUpdatedListener { state ->
                trySend(state)
            }
            try {
                appUpdateManager.registerListener(listener)
            } catch (e: Exception) {
                logError("Failed to register install state listener", e)
                close(e)
            }
            awaitClose {
                try {
                    appUpdateManager.unregisterListener(listener)
                } catch (e: Exception) {
                    logError("Failed to unregister install state listener", e)
                }
            }
        }
            .catch { e ->
                // 只记录错误，不发射任何元素，避免下游异常
                logError("Install state flow error", e)
            }
            .flowOn(Dispatchers.Main)
            .shareIn(internalScope, SharingStarted.Eagerly, replay = 0)
    }

    companion object {
        private const val TAG = "AppUpdateHelper"
        const val UPDATE_REQUEST_CODE = 1234
        // 启动即时更新前的延迟，避免与 Activity 转场动画冲突
        private const val DELAY_BEFORE_IMMEDIATE_UPDATE_MS = 500L
    }

    init {
        registerListenerIfNeeded()
    }

    // ---------- 公开 API ----------

    /**
     * 绑定当前前台 Activity (调用于 Activity.onResume)
     */
    fun setCurrentActivity(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    /**
     * 解绑当前 Activity (调用于 Activity.onPause)
     */
    fun clearCurrentActivity() {
        currentActivityRef = null
    }

    /**
     * 获取更新信息（挂起函数，主线程安全）
     */
    suspend fun checkUpdateInfo(
        updateType: Int,
        requireHighPriority: Boolean = false
    ): AppUpdateInfo? = withContext(Dispatchers.Main) {
        try {
            val info = appUpdateManager.appUpdateInfo.await()
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@withContext null

            val allowed = when (updateType) {
                AppUpdateType.FLEXIBLE -> info.isFlexibleUpdateAllowed
                AppUpdateType.IMMEDIATE -> {
                    info.isImmediateUpdateAllowed && (!requireHighPriority || info.updatePriority() >= 4)
                }
                else -> false
            }
            if (allowed) info else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logError("Failed to get app update info", e)
            null
        }
    }

    /**
     * 检查更新并自动启动流程（推荐在 Activity.onResume 中调用）
     */
    fun checkAndStartUpdate(activity: Activity, updateType: Int = AppUpdateType.FLEXIBLE) {
        if (!isActivityValid(activity)) return

        coroutineScope.launch {
            try {
                val updateInfo = checkUpdateInfo(updateType, requireHighPriority = true)
                if (updateInfo != null) {
                    startUpdateFlow(activity, updateInfo, updateType)
                } else {
                    // 检查是否有已下载待安装的更新（仅灵活更新场景）
                    val info = withContext(Dispatchers.Main) {
                        try {
                            appUpdateManager.appUpdateInfo.await()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            null
                        }
                    }
                    if (info?.installStatus() == InstallStatus.DOWNLOADED) {
                        showCompletionSnackbar(activity)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logError("Unexpected error in checkAndStartUpdate", e)
            }
        }
    }

    /**
     * 恢复被中断的即时更新或显示安装提示 (调用于 Activity.onResume)
     */
    fun onResume(activity: Activity) {
        if (!isActivityValid(activity)) return

        coroutineScope.launch {
            try {
                val info = withContext(Dispatchers.Main) {
                    try {
                        appUpdateManager.appUpdateInfo.await()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }
                } ?: return@launch

                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> {
                        showCompletionSnackbar(activity)
                    }
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        // 恢复即时更新流程
                        if (info.isImmediateUpdateAllowed) {
                            delay(DELAY_BEFORE_IMMEDIATE_UPDATE_MS)
                            if (isActivityValid(activity)) {
                                withContext(Dispatchers.Main) {
                                    appUpdateManager.startUpdateFlowForResult(
                                        info,
                                        AppUpdateType.IMMEDIATE,
                                        activity,
                                        updateRequestCode
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logError("Unexpected error in onResume", e)
            }
        }
    }

    /**
     * 处理 onActivityResult（必须由宿主 Activity 转发）
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == updateRequestCode) {
            if (resultCode != Activity.RESULT_OK) {
                logDebug("Update flow failed or cancelled by user, resultCode=$resultCode")
            }
        }
    }

    /**
     * 完成灵活更新安装
     */
    fun completeUpdate() {
        try {
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            // 注意：这里 CancellationException 不会出现，因为是普通函数
            logError("Failed to complete update", e)
        }
    }

    /**
     * 释放所有资源 (Activity.onDestroy 调用)
     */
    fun cleanup() {
        internalJob.cancel()
        isListenerRegistered.set(false)
        snackbarShowingMap.clear()
        currentActivityRef = null
        logDebug("Cleanup completed")
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 注册安装状态监听器（幂等）
     */
    private fun registerListenerIfNeeded() {
        if (isListenerRegistered.compareAndSet(false, true)) {
            installStateFlow.onEach { state ->
                handleInstallState(state)
            }.launchIn(internalScope)
        }
    }

    /**
     * 处理安装状态变化（日志 + 下载完成主动通知）
     */
    private fun handleInstallState(state: InstallState) {
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                logDebug("Flexible update downloaded, ready to install.")
                // 如果当前有活跃的 Activity，立即弹出安装提示
                currentActivityRef?.get()?.let { activity ->
                    if (isActivityValid(activity)) {
                        showCompletionSnackbar(activity)
                    }
                }
            }
            InstallStatus.INSTALLED -> {
                logDebug("Update installed successfully.")
            }
            InstallStatus.FAILED -> {
                logError("Update failed, error code: ${state.installErrorCode()}")
            }
        }
    }

    /**
     * 启动更新流程（主线程安全）
     */
    private suspend fun startUpdateFlow(activity: Activity, appUpdateInfo: AppUpdateInfo, updateType: Int) {
        if (!isActivityValid(activity)) return

        try {
            withContext(Dispatchers.Main) {
                when (updateType) {
                    AppUpdateType.FLEXIBLE -> {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            AppUpdateType.FLEXIBLE,
                            activity,
                            updateRequestCode
                        )
                    }
                    AppUpdateType.IMMEDIATE -> {
                        delay(DELAY_BEFORE_IMMEDIATE_UPDATE_MS) // 避免与转场动画冲突
                        if (!isActivityValid(activity)) return@withContext
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            AppUpdateType.IMMEDIATE,
                            activity,
                            updateRequestCode
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logError("Failed to start update flow (type=$updateType)", e)
        }
    }

    /**
     * 展示“更新已下载，点击安装” Snackbar
     */
    private fun showCompletionSnackbar(activity: Activity) {
        if (!isActivityValid(activity)) return

        val showingFlag = snackbarShowingMap.getOrPut(activity) { AtomicBoolean(false) }
        if (!showingFlag.compareAndSet(false, true)) {
            logDebug("Snackbar already showing for this activity")
            return
        }

        try {
            val anchorView = findAnchorView(activity) ?: run {
                showingFlag.set(false)
                return
            }

            val message = snackbarMessageText ?: activity.getString(R.string.update_hint)
            val actionText = snackbarActionText ?: activity.getString(R.string.update)

            Snackbar.make(anchorView, message, Snackbar.LENGTH_INDEFINITE)
                .setAction(actionText) { completeUpdate() }
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        showingFlag.set(false)
                        snackbarShowingMap.remove(activity, showingFlag)
                    }
                })
                .show()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logError("Failed to show completion snackbar", e)
            showingFlag.set(false)
        }
    }

    /**
     * 查找用于 Snackbar 的锚点视图，优先使用系统 content 区域
     */
    private fun findAnchorView(activity: Activity): View? {
        // 使用 android.R.id.content，避免依赖项目自定义 ID
        return activity.window?.decorView?.findViewById<View>(android.R.id.content)?.takeIf { it.isAttachedToWindow }
            ?: activity.window?.decorView?.takeIf { it.isAttachedToWindow }
    }

    private fun isActivityValid(activity: Activity): Boolean {
        return !activity.isFinishing && !activity.isDestroyed && activity.window != null
    }

    private fun logDebug(msg: String) {
        Log.d(TAG, msg)
    }

    private fun logError(msg: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, msg, throwable)
        else Log.e(TAG, msg)
    }
}