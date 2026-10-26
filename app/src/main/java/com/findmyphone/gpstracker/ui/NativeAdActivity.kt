package com.findmyphone.gpstracker.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.databinding.ActivityNativeAdBinding

class NativeAdActivity : BaseViewActivity<ActivityNativeAdBinding>() {
    override fun initializeActivity() {
        super.initializeActivity()
        setupNativeAd()
        binding.ivCancle.setOnClickListener {
            finish()
        }
    }
    private fun setupNativeAd() {
        binding.nativeAdWidget.apply {
            setAdUnitId(AreaKey.openMainNativeAdv)
            bindToLifecycle(this@NativeAdActivity)
            // 启动自动刷新（开始加载并每隔30秒刷新一次）
            startAutoRefresh()
        }
    }
    override fun onDestroy() {
        super.onDestroy()

    }

}