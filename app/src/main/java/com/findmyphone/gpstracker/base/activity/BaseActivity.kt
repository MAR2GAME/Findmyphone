package com.findmyphone.gpstracker.base.activity

import android.Manifest

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.LocaleHelper
import com.findmyphone.gpstracker.view.ProgressDialog

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.jvm.javaClass
import kotlin.text.contains
import kotlin.to

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.setLocaleForApplication(newBase))
    }

    var progressDialog: ProgressDialog? = null

    fun handleLoadingState(showLoading: Boolean) {
        if (progressDialog == null) {
            progressDialog = ProgressDialog(this)
        }
        if (showLoading) {
            progressDialog?.showProgress()
        } else {
            progressDialog?.hideProgress()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityManagerUtils.addActivity(this)
    }

    override fun onDestroy() {
        progressDialog?.hideProgress()
        progressDialog = null
        ActivityManagerUtils.removeActivity(this)
        super.onDestroy()
    }

}