package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.FriendLocationBeans
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpException

import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.to

class LocationsListViewModel(application: Application) : BaseViewModel(application) {
    val isLoading = MutableLiveData<Boolean>()
    var locationsData=MutableLiveData<FriendLocationBeans?>()
    fun getLocationsData(page:Int) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val mutableMap = mutableMapOf("page" to page, "size" to 10)
                val friendLocationBeans =
                    HttpClient.getForData<FriendLocationBeans>(
                        AppConfig.BASE_URL + "/api/v1/friends/locations",
                        mutableMap
                    )
                if (friendLocationBeans != null) {
                    locationsData.value = friendLocationBeans
                } else {
                    locationsData.value = null
                }
            } catch (e: HttpException) {
                locationsData.value = null
                Toast.makeText(
                    application,
                    application.getString(R.string.request_error), Toast.LENGTH_SHORT
                )
                    .show()
                // 处理 HTTP 错误
            } catch (e: IOException) {
                locationsData.value = null
                // 处理网络错误
                Toast.makeText(
                    application,
                    application
                        .getString(R.string.check_your_network),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                locationsData.value = null
                Toast.makeText(
                    application,
                    application.getString(R.string.request_error),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isLoading.value = false
            }
        }

    }



}