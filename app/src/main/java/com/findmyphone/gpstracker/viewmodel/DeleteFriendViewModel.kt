package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.FriendLocationBeansItem
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpClient.gson
import com.findmyphone.gpstracker.request.HttpException
import kotlinx.coroutines.launch
import java.io.IOException

class DeleteFriendViewModel(application: Application) : BaseViewModel(application) {

    val isLoading = MutableLiveData<Boolean>()
    val isDeleteState = MutableLiveData<Boolean>()
    // 一次性错误事件，避免重复弹 Toast
    val errorEvent = MutableLiveData<Event<String>>()

    fun deleteFriend(friend: FriendLocationBeansItem) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val json = gson.toJson(friend)
                HttpClient.postJsonForData<Any>(AppConfig.BASE_URL + "/api/v1/friends/remove", json)
                isDeleteState.value = true
            } catch (e: HttpException) {
                isDeleteState.value = false
                errorEvent.value = Event(getApplication<Application>().getString(R.string.deleted_failed))
            } catch (e: IOException) {
                isDeleteState.value = false
                errorEvent.value = Event(getApplication<Application>().getString(R.string.check_your_network))
            } catch (e: Exception) {
                isDeleteState.value = false
                errorEvent.value = Event(getApplication<Application>().getString(R.string.deleted_failed))
            } finally {
                isLoading.value = false
            }
        }
    }

    // 事件包装类，保证只消费一次
    class Event<out T>(private val content: T) {
        private var hasBeenHandled = false
        fun getContentIfNotHandled(): T? {
            return if (hasBeenHandled) null
            else {
                hasBeenHandled = true
                content
            }
        }
    }
}