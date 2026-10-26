package com.findmyphone.gpstracker.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.isNetworkAvailable
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.databinding.ActivitySearchPhoneBinding
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.viewmodel.SearchPhoneViewModel

class SearchPhoneActivity : BaseMvvmActivity<ActivitySearchPhoneBinding, SearchPhoneViewModel>() {

    private var isValidPhone = false

    override fun init() {
        initViews()
        observeViewModel()
        setupBackPressHandler()
        setupNativeAd()
    }

    private fun setupNativeAd() {
        binding.nativeAdWidget.apply {
            setLayoutResId(R.layout.view_native_ad_two,2)
            setAdUnitId(AreaKey.phoneSearchNoticeBtmNativeAdv)
            bindToLifecycle(this@SearchPhoneActivity)
            // 启动自动刷新（开始加载并每隔30秒刷新一次）
            startAutoRefresh()
        }
    }

    private fun initViews() {
        binding.apply {
            // 输入监听 & 清除按钮
            etPhone.doAfterTextChanged { text ->
                ivDelete.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            ivDelete.setOnClickListener { etPhone.text.clear() }

            // 国家选择器初始化
            ccp.registerCarrierNumberEditText(etPhone)
            ccp.setPhoneNumberValidityChangeListener { isValid ->
                isValidPhone = isValid
            }

            // 返回按钮
            ivBack.setOnClickListener {
                readyGoThenKill(MainActivity::class.java)
            }

            // 搜索按钮
            btSearch.setOnClickListener {
                performSearch()
            }
        }
    }

    private fun observeViewModel() {
        // 国家代码自动选择
        viewModel.countryCode.observe(this) { countryCode ->
            countryCode?.let { binding.ccp.setCountryForNameCode(it) }
        }

        // 仅查询加载状态影响全局 loading
        viewModel.isQueryLoading.observe(this) { isLoading ->
            handleLoadingState(isLoading)
        }

        // 查询结果事件
        viewModel.queryResultEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { phoneInfo ->
                showInterstitialAdWithStats(AreaKey.phoneSearchClickAdv) {
                    if (phoneInfo != null) {
                        val bundle = Bundle().apply {
                            putParcelable("PhoneNumberInfo", phoneInfo)
                        }
                        readyGoThenKill(PhoneInfoActivity::class.java, bundle)
                    } else {
                        Toast.makeText(this, R.string.query_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        // 初始化获取本机 IP 对应国家码
        viewModel.fetchCountryCodeIfNeeded()
    }

    private fun performSearch() {
        LogUtil.log("phone_number_click_search",emptyMap())
        if (!isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.check_your_network, Toast.LENGTH_SHORT).show()
            return
        }
        if (!isValidPhone) {
            Toast.makeText(this, R.string.enter_valid_mobile, Toast.LENGTH_SHORT).show()
            return
        }
        val fullNumber = binding.ccp.fullNumberWithPlus
        val selectedCountry = binding.ccp.selectedCountryNameCode ?: "US"
        viewModel.performLookup(fullNumber, selectedCountry)
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                readyGoThenKill(MainActivity::class.java)
            }
        })
    }
}