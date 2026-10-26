package com.findmyphone.gpstracker.base.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel



open class BaseViewModel(application: Application): AndroidViewModel(application) {


    /**
     * ViewModel销毁同时也取消请求
     */
    override fun onCleared() {
        super.onCleared()

    }


}