package com.findmyphone.gpstracker.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.base.command.readyGo
import com.findmyphone.gpstracker.base.command.safeClick
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.databinding.ActivityAboutBinding
import com.findmyphone.gpstracker.utils.LogUtil

class AboutActivity : BaseViewActivity<ActivityAboutBinding> (){

    override fun initializeActivity() {
        super.initializeActivity()

        setupVersionInfo()

        binding.ivBack.safeClick {
            finish()
        }

        binding.tvTerms.safeClick {
            LogUtil.log("check_terms", mapOf("referrer_name" to "about"))
            openTermsOrPrivacyWebView(AppConfig.TERMS_URL)
        }
        binding.tvPrivacy.safeClick {
            LogUtil.log("check_privacy", mapOf("referrer_name" to "about"))
            openTermsOrPrivacyWebView(AppConfig.PRIVACY_URL)
        }
    }
    private fun openTermsOrPrivacyWebView(url: String) {
        val bundle = Bundle().apply {
            putString(WebViewActivity.EXTRA_URL, url)
            putString("tag", "about")
        }
        readyGo(WebViewActivity::class.java, bundle)
    }

    private fun setupVersionInfo() {
        binding.tvCode.text=getString(R.string.version)+getAppVersionName()
    }
    private fun getAppVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: PackageManager.NameNotFoundException) {
            // 使用更合适的日志记录方式
            e.printStackTrace()
            ""
        }
    }

}