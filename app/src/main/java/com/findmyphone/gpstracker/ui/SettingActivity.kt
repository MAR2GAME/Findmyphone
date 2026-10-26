package com.findmyphone.gpstracker.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseActivity
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.base.command.rateUS
import com.findmyphone.gpstracker.base.command.readyGo
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.databinding.ActivitySettingBinding
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.view.RateUsDialog

class SettingActivity : BaseViewActivity<ActivitySettingBinding>() {
    private var rateUsDialog: RateUsDialog?=null

    override fun initializeActivity() {
        super.initializeActivity()
        setupClickListeners()
    }

    fun setupClickListeners(){
        binding.ivBack.setOnClickListener {
           finish()
        }

        binding.rlEdit.setOnClickListener {
            showInterstitialAdWithStats(AreaKey.enterSettingDeviceNameAdv) {
                val builder= Bundle()
                builder.putInt("type",1)
                readyGo(LoginActivity::class.java,builder)
            }
        }
        binding.rlLanguage.setOnClickListener {
            showInterstitialAdWithStats(AreaKey.enterSettingLanguageAdv) {
                readyGo(SetLanguageActivity::class.java)
            }
        }

        binding.rlRate.setOnClickListener {
            LogUtil.log("rate_popup", emptyMap())
            if(rateUsDialog==null){
                rateUsDialog= RateUsDialog(this)
                rateUsDialog!!.setOnRateClickListener(object : RateUsDialog.OnRateClickListener{
                    override fun onRateClicked(rating: Int) {
                        Toast.makeText(this@SettingActivity, getString(R.string.feedback), Toast.LENGTH_SHORT)
                            .show()

                        if(rating==5){
                            rateUS()
                        }
                        rateUsDialog!!.dismiss()
                    }
                })
            }

            rateUsDialog!!.showDialog()
        }
        binding.rlAbout.setOnClickListener {
            readyGo(AboutActivity::class.java)
        }
        binding.rlPricacy.setOnClickListener {
            openTermsOrPrivacyWebView(AppConfig.PRIVACY_URL)
        }

      //  setupNativeAd()


    }

    private fun setupNativeAd() {
//        binding.nativeAdWidget.apply {
//            setAdUnitId(AreaKey.settingNativeAdv)
//            setLayoutResId(R.layout.view_native_ad_six,3)
//            bindToLifecycle(this@SettingActivity)
//            // 启动自动刷新（开始加载并每隔30秒刷新一次）
//            startAutoRefresh()
//        }
    }



    override fun onDestroy() {
        rateUsDialog?.release()
        rateUsDialog=null
        super.onDestroy()
    }

    private fun openTermsOrPrivacyWebView(url: String) {
        val bundle = Bundle().apply {
            putString(WebViewActivity.EXTRA_URL, url)
            putString("tag", "setting")
        }
        readyGo(WebViewActivity::class.java, bundle)
    }

}