package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.CheckUserData
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpException
import kotlinx.coroutines.launch
import java.io.IOException

class AddFriendViewModel(application: Application) : BaseViewModel(application) {
    val isLoading = MutableLiveData<Boolean>()
    val isCheckState = MutableLiveData<Boolean>()
    val errorMessage = MutableLiveData<String>()

    fun searchFriend(code: String) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val mutableMap = mutableMapOf("securityCode" to code)
                val checkUserData = HttpClient.getForData<CheckUserData>(
                    AppConfig.BASE_URL + "/api/v1/users/exists-by-security-code",
                    mutableMap
                )
                // 接口成功但 data 为 null（理论上不应发生，做防御处理）
                if (checkUserData == null) {
                    isCheckState.value = false
                    errorMessage.value = getApplication<Application>()
                        .getString(R.string.enter_correct_code)
                    return@launch
                }
                isCheckState.value = checkUserData.exists
                if (!checkUserData.exists) {
                    errorMessage.value = getApplication<Application>()
                        .getString(R.string.enter_correct_code)
                }
            } catch (e: HttpException) {
                isCheckState.value = false
                // 优先使用服务器返回的错误消息，若为空则用通用提示
                val msg = e.message?.takeIf { it.isNotBlank() }
                    ?: getApplication<Application>().getString(R.string.request_error)
                errorMessage.value = msg
            } catch (e: IOException) {
                isCheckState.value = false
                errorMessage.value = getApplication<Application>()
                    .getString(R.string.check_your_network)
            } catch (e: Exception) {
                isCheckState.value = false
                errorMessage.value = getApplication<Application>()
                    .getString(R.string.request_error)
            }
        }
    }
}