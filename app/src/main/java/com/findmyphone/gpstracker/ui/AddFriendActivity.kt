package com.findmyphone.gpstracker.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.addTextChangedListener
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.readyGo
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.databinding.ActivityAddFriendBinding
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.viewmodel.AddFriendViewModel

class AddFriendActivity : BaseMvvmActivity<ActivityAddFriendBinding, AddFriendViewModel>() {
    override fun init() {
        initViewModel()
        setupBackPressHandler()
        setupEditText()
        setupClickListeners()
        setupNativeAd()
        LogUtil.log("enter_input_code_page", emptyMap())
    }
    private fun setupEditText() {
        binding.etCode.addTextChangedListener(afterTextChanged = { text ->
            binding.ivClose.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        })
        binding.ivClose.setOnClickListener {
            binding.etCode.text?.clear()
        }
    }

    private fun setupNativeAd() {
        binding.nativeAdWidget.apply {
            setLayoutResId(R.layout.view_native_ad_two,2)
            setAdUnitId(AreaKey.inputCodeBtnBtmNativeAdv)
            bindToLifecycle(this@AddFriendActivity)
            // 启动自动刷新（开始加载并每隔30秒刷新一次）
            startAutoRefresh()
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            onBack()
        }
        binding.btNext.setOnClickListener {
            LogUtil.log("inputcode_page_click_next", emptyMap())
            val code = binding.etCode.text.toString().trim()
            if (TextUtils.isEmpty(code)) {
                Toast.makeText(
                    this,
                    getString(R.string.enter_correct_code),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            viewModel.searchFriend(code)
        }
        binding.tvSearch.setOnClickListener {
            readyGo(ScannerActivity::class.java)
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    private fun onBack() {
        showInterstitialAdWithStats(AreaKey.inputCodeBackHomeAdv){
            finish()
        }
    }

    private fun initViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            handleLoadingState(isLoading)
        }

        viewModel.isCheckState.observe(this) { isExists ->
            if (isExists) {
                showInterstitialAdWithStats(AreaKey.enterDisplayNameAdv){
                    val bundle = Bundle().apply {
                        putString("code", binding.etCode.text.toString().trim())
                    }
                    readyGoThenKill(EditFriendActivity::class.java, bundle)
                }
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            handleLoadingState(false)
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }
}