package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.text.TextUtils
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.command.dp2px
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.FcmTokenBean
import com.findmyphone.gpstracker.bean.UserCodeBean
import com.findmyphone.gpstracker.bean.UserInfo
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpClient.gson
import com.findmyphone.gpstracker.request.HttpException
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.QrCodeGenerator
import kotlinx.coroutines.launch
import java.io.IOException

class MianViewModel (application: Application) : BaseViewModel(application){
    fun saveFCMToken(){
        viewModelScope.launch {
            if(AppPrefsUtils.getString("OLD_FCM_TOKEN")!=AppPrefsUtils.getString("FCM_TOKEN")){
                AppPrefsUtils.commitString("OLD_FCM_TOKEN",AppPrefsUtils.getString("FCM_TOKEN"))
                val req = FcmTokenBean(AppPrefsUtils.getString("FCM_TOKEN"))
                val json = gson.toJson(req)
                try {
                    HttpClient.postJsonForData<Any>(
                        AppConfig.BASE_URL + "/api/v1/users/devices/fcm",
                        json
                    )
                } catch (e: Exception) {
                }
            }
        }
    }

    val isLoading = MutableLiveData<Boolean>()

    var codeData=MutableLiveData<UserCodeBean>()

    private var cachedQrBitmap: Bitmap? = null


    fun getUserCode(){
        viewModelScope.launch {
            isLoading.value=true
            val userCodeBean= UserCodeBean("")
            try {
                if(!TextUtils.isEmpty(AppPrefsUtils.getString(AppConfig.USER_CODE))){
                    userCodeBean.code= AppPrefsUtils.getString(AppConfig.USER_CODE)
                }else{
                    val userInfo = HttpClient.getForData<UserInfo>(AppConfig.BASE_URL+"/api/v1/users/me")
                    if(userInfo!=null){
                        userCodeBean.code=userInfo.securityCode
                        AppPrefsUtils.commitString(AppConfig.USER_CODE,userCodeBean.code)
                        // 安全码变化，清除旧的二维码缓存
                        clearQrCache()
                    }
                }
                if(cachedQrBitmap!=null){
                    userCodeBean.bitmap=cachedQrBitmap
                }else{
                    if(!TextUtils.isEmpty(userCodeBean.code)){
                        val bitmap = generateQrCode(userCodeBean.code)
                        if (bitmap != null) {
                            cachedQrBitmap = bitmap
                            userCodeBean.bitmap = bitmap
                        } else {
                            userCodeBean.bitmap = null   // 生成失败
                        }
                    }
                }
                codeData.value=userCodeBean
            }catch (e: HttpException) {
                Toast.makeText(application,
                    application.getString(R.string.request_error), Toast.LENGTH_SHORT)
                    .show()
                // 处理 HTTP 错误
            } catch (e: IOException) {
                // 处理网络错误
                Toast.makeText(application, application.getString(R.string.check_your_network), Toast.LENGTH_SHORT).show()
            }catch (e: Exception){
                Toast.makeText(application, application.getString(R.string.request_error), Toast.LENGTH_SHORT).show()
            } finally {
                isLoading.value=false
            }
        }

    }

    private fun clearQrCache() {
        cachedQrBitmap?.recycle()
        cachedQrBitmap = null
    }

    // 生成二维码（可自定义尺寸、颜色等参数）
    private suspend fun generateQrCode(code: String): Bitmap? {
        return QrCodeGenerator.createQrCode(
            content = code,
            size = dp2px(application,100),          // 根据 UI 需求调整
            keepMargin = false,
            codeColor = Color.BLACK,
            bgColor = Color.WHITE
        )
    }

    // 页面销毁时释放缓存
    override fun onCleared() {
        super.onCleared()
        clearQrCache()
    }

}