package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.UserInfo
import com.findmyphone.gpstracker.bean.ZoneListBean
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpException

import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.collections.sortBy
import kotlin.to

class ZoneListViewModel (application: Application) : BaseViewModel(application)  {
    val isLoading = MutableLiveData<Boolean>()

    val zoonListData=MutableLiveData<ZoneListBean?>()


    fun getZoonListData(){
        viewModelScope.launch {
            isLoading.value = true
            try {
                val mutableMap = mutableMapOf("page" to 1, "size" to 10)
                val zoneListBeans =
                    HttpClient.getForData<ZoneListBean>(AppConfig.BASE_URL + "/api/v1/zones",mutableMap)
                if(zoneListBeans!=null){
                    zoneListBeans.items.sortBy { it.zoneId }
                    zoonListData.value=zoneListBeans
                }else{
                    zoonListData.value = null
                }
            } catch (e: HttpException) {
                zoonListData.value = null
                Toast.makeText(application,
                    application.getString(R.string.request_error), Toast.LENGTH_SHORT)
                    .show()
                // 处理 HTTP 错误
            } catch (e: IOException) {
                zoonListData.value = null
                // 处理网络错误
                Toast.makeText(
                    application,
                    application
                        .getString(R.string.check_your_network),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                zoonListData.value = null
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