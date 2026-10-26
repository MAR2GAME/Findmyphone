package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.PhoneNumberInfo
import com.findmyphone.gpstracker.utils.IpGeoClient
import com.findmyphone.gpstracker.utils.PhoneNumberLookupService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 一次性事件封装，防止重复消费
 */
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    fun peekContent(): T = content
}

class SearchPhoneViewModel(application: Application) : BaseViewModel(application) {

    private val _countryCode = MutableLiveData<String>()
    val countryCode: LiveData<String> = _countryCode

    private val _isIpLoading = MutableLiveData<Boolean>()
    val isIpLoading: LiveData<Boolean> = _isIpLoading

    private val _isQueryLoading = MutableLiveData<Boolean>()
    val isQueryLoading: LiveData<Boolean> = _isQueryLoading

    private val _queryResultEvent = MutableLiveData<Event<PhoneNumberInfo?>>()
    val queryResultEvent: LiveData<Event<PhoneNumberInfo?>> = _queryResultEvent

    private val lookupService = PhoneNumberLookupService()
    private var lookupJob: Job? = null

    init {
        fetchCountryCodeIfNeeded()
    }

    /**
     * 仅当国家码尚未获取时才通过 IP 定位获取
     */
    fun fetchCountryCodeIfNeeded() {
        if (_countryCode.value != null) return
        viewModelScope.launch {
            _isIpLoading.value = true
            val ipInfo = IpGeoClient.getIpInfo()
            _countryCode.value = if (ipInfo != null && ipInfo.status == "success") {
                ipInfo.countryCode
            } else {
                "US" // 默认
            }
            _isIpLoading.value = false
        }
    }

    /**
     * 执行号码查询，自动取消上一次未完成的查询
     */
    fun performLookup(fullNumber: String, selectedCountry: String) {
        // 取消前一次查询，保证结果顺序
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            _isQueryLoading.value = true
            val info = lookupService.lookupPhoneNumber(fullNumber, selectedCountry)
            _queryResultEvent.value = Event(info)
            _isQueryLoading.value = false
        }
    }
}