package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpClient.gson
import kotlinx.coroutines.launch
import java.io.IOException

class SosActivityViewModel(application: Application) : BaseViewModel(application)  {
    private val _remainingSeconds = MutableLiveData(10)
    val remainingSeconds: LiveData<Int> = _remainingSeconds

    private val _isCountingDown = MutableLiveData(true)
    val isCountingDown: LiveData<Boolean> = _isCountingDown

    fun decrement() {
        val current = _remainingSeconds.value ?: 0
        if (current > 0) {
            _remainingSeconds.value = current - 1
        }
        if (current - 1 <= 0) {
            _isCountingDown.value = false
        }
    }


    fun senSos(message: String){
        viewModelScope.launch {
            try {
                val requestBody = mapOf(
                    "message" to message
                )
                val json = gson.toJson(requestBody)
                HttpClient.postJsonForData<Any>(
                    url = AppConfig.BASE_URL + "/api/v1/sos",
                    jsonBody = json
                )
            } catch (e: Exception) {
                // 静默失败
            }
        }

    }

    fun reset() {
        _remainingSeconds.value = 10
        _isCountingDown.value = true
    }


}