package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.SplashEvent
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(application) {

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL = 20L
        private const val TOTAL_PROGRESS = 100
        private const val KEY_PROGRESS = "splash_progress"
    }

    // 进度状态
    private val _progress = MutableStateFlow(
        savedStateHandle.get<Int>(KEY_PROGRESS) ?: 0
    )
    val progress: StateFlow<Int> = _progress

    val progressText: StateFlow<String> = MutableStateFlow("${_progress.value}%").apply {
        viewModelScope.launch {
            _progress.collect { value = "$it%" }
        }
    }

    // 事件通道（仅用于用户点击条款/隐私，导航不再使用事件流防止丢失）
    private val _events = MutableSharedFlow<SplashEvent>()
    val events = _events.asSharedFlow()

    // 暂停/恢复控制
    private val _isResumed = MutableStateFlow(true)
    private var progressJob: Job? = null
    private var isSequenceStarted = false

    /** 获取当前导航目标（由 Activity 主动调用） */
    fun getNavigationDestination(): SplashEvent? {
        if (_progress.value < TOTAL_PROGRESS) return null
        val isUserSet = AppPrefsUtils.getBoolean(AppConfig.IS_SET_USER, false)
        return if (!isUserSet) SplashEvent.GoToSetLanguage else SplashEvent.GoToMain
    }

    /** 开始进度模拟（由 Activity 权限就绪后调用） */
    fun startSequence() {
        if (isSequenceStarted) return
        isSequenceStarted = true
        simulateProgressAndNavigate()
    }

    /** 暂停进度（Activity.onStop 调用） */
    fun pauseProgress() {
        _isResumed.value = false
    }

    /** 恢复进度（Activity.onStart 调用） */
    fun resumeProgress() {
        _isResumed.value = true
    }

    fun onTermsClicked() {
        emitEvent(SplashEvent.OpenWebView(AppConfig.TERMS_URL))
    }

    fun onPrivacyClicked() {
        emitEvent(SplashEvent.OpenWebView(AppConfig.PRIVACY_URL))
    }

    private fun simulateProgressAndNavigate() {
        val current = _progress.value
        if (current >= TOTAL_PROGRESS) return // 已达100%无需再模拟

        progressJob = viewModelScope.launch {
            for (i in current until TOTAL_PROGRESS) {
                // 如果暂停，则挂起直到恢复
                if (!_isResumed.value) {
                    _isResumed.first { it }   // 等待变为 true
                }
                delay(PROGRESS_UPDATE_INTERVAL)
                _progress.value = i + 1
                savedStateHandle[KEY_PROGRESS] = i + 1
            }
            // 进度到达100%后，由 Activity 通过 progress 流检测并导航
        }
    }

    private fun emitEvent(event: SplashEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
    }
}