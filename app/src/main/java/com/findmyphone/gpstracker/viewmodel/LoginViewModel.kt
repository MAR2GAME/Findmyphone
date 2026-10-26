package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpException
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import kotlinx.coroutines.launch
import java.io.IOException

class LoginViewModel(application: Application) : BaseViewModel(application) {

    // 用密封类定义一次性 UI 事件，不再直接从 ViewModel 弹出 Toast
    sealed class RegisterEvent {
        object Loading : RegisterEvent()
        object Success : RegisterEvent()
        data class Error(val message: String) : RegisterEvent()
    }

    private val _registerEvent = MutableLiveData<RegisterEvent>()
    val registerEvent: LiveData<RegisterEvent> = _registerEvent

    fun registerDevice(deviceCode: String, nickname: String) {
        viewModelScope.launch {
            _registerEvent.value = RegisterEvent.Loading
            try {
                HttpClient.registerDevice(deviceCode, nickname)
                AppPrefsUtils.commitBoolean(AppConfig.IS_SET_USER, true)
                _registerEvent.value = RegisterEvent.Success
            } catch (e: HttpException) {
                _registerEvent.value = RegisterEvent.Error(
                    application.getString(R.string.request_error)
                )
            } catch (e: IOException) {
                _registerEvent.value = RegisterEvent.Error(
                    application.getString(R.string.check_your_network)
                )
            }
        }
    }
}