package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.ZoneItem
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpClient.gson
import com.findmyphone.gpstracker.request.HttpException

import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.to

class ZoneDetailsViewModel(application: Application) : BaseViewModel(application){

    val isLoading = MutableLiveData<Boolean>()

    val isSaveState=MutableLiveData<Boolean>()

    val isDeleteState=MutableLiveData<Boolean>()



    fun save(zoneItem: ZoneItem){
        viewModelScope.launch {
            isLoading.value = true
            try {
                val json = gson.toJson(zoneItem)
                val save= HttpClient.postJsonForData<ZoneItem>(AppConfig.BASE_URL+"/api/v1/zones/save",json)
                if(save!=null){
                    isSaveState.value=true
                }else{
                    isSaveState.value=false
                }
            } catch (e: HttpException) {
                isSaveState.value=false
                Toast.makeText(application,
                    application.getString(R.string.request_error), Toast.LENGTH_SHORT)
                    .show()
                // 处理 HTTP 错误
            } catch (e: IOException) {
                isSaveState.value=false
                // 处理网络错误
                Toast.makeText(
                    application,
                    application
                        .getString(R.string.check_your_network),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                isSaveState.value=false
                Toast.makeText(
                    application,
                    application.getString(R.string.request_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }



    fun delete(zoneId: Long){
        viewModelScope.launch {
            isLoading.value = true
            try {
                val mutableMap = mutableMapOf("zoneId" to zoneId)
                val delete= HttpClient.deleteForData<Any>(AppConfig.BASE_URL+"/api/v1/zones/{zoneId}",mutableMap)
                isDeleteState.value=true
            } catch (e: HttpException) {
                isDeleteState.value=true
                Toast.makeText(application,
                    application.getString(R.string.request_error), Toast.LENGTH_SHORT)
                    .show()
                // 处理 HTTP 错误
            } catch (e: IOException) {
                isDeleteState.value=true
                // 处理网络错误
                Toast.makeText(
                    application,
                    application
                        .getString(R.string.check_your_network),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                isDeleteState.value=true
                Toast.makeText(
                    application,
                    application.getString(R.string.request_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}