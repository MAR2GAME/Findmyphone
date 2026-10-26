package com.findmyphone.gpstracker.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AdvCheckManager
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.bean.NativeAdClickEvent
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.databinding.ActivityLoginBinding
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.DeviceUniqueIdUtils
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.utils.NativeAdConfigManager
import com.findmyphone.gpstracker.viewmodel.LoginViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class LoginActivity : BaseMvvmActivity<ActivityLoginBinding, LoginViewModel>() {

    companion object {
        private const val TYPE_FROM_GUIDE = 0
        private const val TYPE_FROM_SETTINGS = 1
    }

    private var isRegistering = false  // 防重复点击标志
    var intentType =0

    override fun init() {
        intentType=intent.getIntExtra("type", TYPE_FROM_GUIDE)
        if (intentType == TYPE_FROM_SETTINGS) {
            binding.ivBack.visibility = View.VISIBLE
            binding.ivBack.setOnClickListener {
                finish()
            }
        }
        EventBus.getDefault().register(this)
        setupNameInput()
        setupNextButton()
        observeViewModel()
        setupNativeAd()
        setupBackPressHandler()
        LogUtil.log("enter_login_page", emptyMap())


    }


    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    private var clickSize=0
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: NativeAdClickEvent) {
        if(NativeAdConfigManager.containsAreaKey(AreaKey.deviceNameSetBtmNativeAdv)){
            clickSize++
            if(clickSize>= NativeAdConfigManager.getClickSize(AreaKey.deviceNameSetBtmNativeAdv)){
                lifecycleScope.launch {
                    delay(500)
                    binding.nativeAdWidget.stopAutoRefresh()
                    binding.nativeAdWidget.visibility= View.GONE
                }
            }
        }
    }


    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 屏蔽返回键
                if (intentType == TYPE_FROM_SETTINGS){
                    finish()
                }
            }
        })
    }

    private fun setupNativeAd() {
        binding.nativeAdWidget.apply {
            setLayoutResId(R.layout.view_native_ad_two, 2)
            setAdUnitId(AreaKey.deviceNameSetBtmNativeAdv)
            bindToLifecycle(this@LoginActivity)
            startAutoRefresh()
        }
    }

    // ---------- 昵称输入区域 ----------
    private fun setupNameInput() {
        // 控制清除按钮可见性
        binding.etName.addTextChangedListener(
            afterTextChanged = { text ->
                binding.ivClose.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        )

        // 设置默认名称（品牌+型号），若已有昵称则沿用
        val savedName = AppPrefsUtils.getString(AppConfig.NICK_NAME)
        val defaultName = if (savedName.isNullOrEmpty()) {
            "${Build.BRAND} ${Build.MODEL}"
        } else {
            savedName
        }
        binding.etName.setText(defaultName)

        // 安全设置光标位置（避免因 InputFilter 等导致越界）
        binding.etName.text?.length?.takeIf { it > 0 }?.let { length ->
            binding.etName.setSelection(length)
        }

        binding.ivClose.setOnClickListener {
            binding.etName.text?.clear()
        }
    }

    // ---------- 下一步按钮 ----------
    private fun setupNextButton() {
        binding.btNext.setOnClickListener {
            if (isRegistering) {
                // 正在注册中，避免重复点击
                return@setOnClickListener
            }

            val name = binding.etName.text.toString().trim()
            if (name.isNotBlank()) {
                val code = DeviceUniqueIdUtils.getDeviceUniqueId()
                AppPrefsUtils.commitString(AppConfig.DEVICE_CODE, code)
                AppPrefsUtils.commitString(AppConfig.NICK_NAME, name)
                LogUtil.log("setup_device_name", mapOf("name" to name))
                isRegistering = true
                viewModel.registerDevice(code, name)
            } else {
                Toast.makeText(this, R.string.set_up_name, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- 观察 ViewModel 事件 ----------
    private fun observeViewModel() {
        viewModel.registerEvent.observe(this) { event ->
            when (event) {
                is LoginViewModel.RegisterEvent.Loading -> handleLoadingState(true)
                is LoginViewModel.RegisterEvent.Success -> {
                    handleLoadingState(false)
                    isRegistering = false
                    navigateToMainWithAd()
                }
                is LoginViewModel.RegisterEvent.Error -> {
                    handleLoadingState(false)
                    isRegistering = false
                    Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
                    navigateToMainWithAd()
                }
            }
        }
    }
    private fun navigateToMainWithAd() {
        if(intentType != TYPE_FROM_SETTINGS){
            if(AdvCheckManager.params.fromNature){
                AppPrefsUtils.commitBoolean(AppConfig.IS_SET_USER, true)
            }else{
                if (AppPrefsUtils.getBoolean("is_First_to_guide", true)) {
                    AppPrefsUtils.commitBoolean("is_First_to_guide", false)
                } else {
                    AppPrefsUtils.commitBoolean(AppConfig.IS_SET_USER, true)
                }
            }
            showInterstitialAdWithStats(AreaKey.newDeviceNameSetFinishAdv) {
                readyGoThenKill(MainActivity::class.java)
            }
        }else{
            showInterstitialAdWithStats(AreaKey.newDeviceNameSetFinishAdv) {
                readyGoThenKill(SettingActivity::class.java)
            }
        }
    }
}