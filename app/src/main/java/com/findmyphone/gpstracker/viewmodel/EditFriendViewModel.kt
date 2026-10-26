package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.AddFriendReq
import com.findmyphone.gpstracker.bean.AddFriendRsp
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpException

import kotlinx.coroutines.launch
import java.io.IOException

class EditFriendViewModel(application: Application) : BaseViewModel(application) {

    // 用密封类统一描述请求结果
    sealed class AddFriendResult {
        object Loading : AddFriendResult()
        object Success : AddFriendResult()
        data class Error(val errorMsgResId: Int) : AddFriendResult()
    }

    private val _addFriendResult = MutableLiveData<Event<AddFriendResult>>()
    val addFriendResult: LiveData<Event<AddFriendResult>> = _addFriendResult

    fun addFriend(friendSecurityCode: String, displayName: String, gender: String) {
        // 前置校验
        if (friendSecurityCode.isBlank() || displayName.isBlank()) {
            _addFriendResult.value = Event(AddFriendResult.Error(R.string.enter_display_name))
            return
        }

        viewModelScope.launch {
            _addFriendResult.value = Event(AddFriendResult.Loading)
            try {
                val req = AddFriendReq(friendSecurityCode, displayName, gender)
                val json = HttpClient.gson.toJson(req)
                val addFriendRsp = HttpClient.postJsonForData<AddFriendRsp>(
                    "${AppConfig.BASE_URL}/api/v1/friends/add", json
                )
                if (addFriendRsp != null) {
                    _addFriendResult.value = Event(AddFriendResult.Success)
                } else {
                    _addFriendResult.value = Event(AddFriendResult.Error(R.string.request_error))
                }
            } catch (e: HttpException) {
                _addFriendResult.value = Event(AddFriendResult.Error(R.string.request_error))
            } catch (e: IOException) {
                _addFriendResult.value = Event(AddFriendResult.Error(R.string.check_your_network))
            } catch (e: Exception) {
                _addFriendResult.value = Event(AddFriendResult.Error(R.string.request_error))
            }
        }
    }
}