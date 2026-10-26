package com.findmyphone.gpstracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.adapter.LanguageAdapter
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.checkNotificationPermission
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.base.command.safeClick
import com.findmyphone.gpstracker.bean.NativeAdClickEvent
import com.findmyphone.gpstracker.bean.SetLanguageEvent
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.databinding.ActivitySetLanguageBinding
import com.findmyphone.gpstracker.services.PersistentNotificationService
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.LocaleHelper
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.utils.NativeAdConfigManager
import com.findmyphone.gpstracker.view.SetLanguageDialog
import com.findmyphone.gpstracker.viewmodel.SetLanguageViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.jvm.java

class SetLanguageActivity : BaseMvvmActivity<ActivitySetLanguageBinding, SetLanguageViewModel>() {
    private lateinit var adapter: LanguageAdapter

    private var setLanguageDialog: SetLanguageDialog?=null
    private var inactivityJob: Job? = null


    override fun init() {
        EventBus.getDefault().register(this)
        initAdapter()
        setupObservers()
        setupNativeAd()
        // 初始设置标题与按钮文字（语言已就绪）
        binding.tvTitle.text = getString(R.string.language)
        binding.tvSave.text = getString(R.string.save)
        startInactivityTimer()   // 启动无操作计时
        LogUtil.log("enter_setLanguage_page", emptyMap())

    }

    private var clickSize=0
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: NativeAdClickEvent) {
        if(NativeAdConfigManager.containsAreaKey(AreaKey.languageSetBtmNativeAdv)){
            clickSize++
            if(clickSize>= NativeAdConfigManager.getClickSize(AreaKey.languageSetBtmNativeAdv)){
                lifecycleScope.launch {
                    delay(500)
                    binding.nativeAdWidget.stopAutoRefresh()
                    binding.nativeAdWidget.visibility= View.GONE
                }
            }
        }
    }


    // ---------- 无操作检测相关 ----------

    private fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = lifecycleScope.launch {
            delay(10_000L) // 10秒
            // 确保页面仍在前台且未销毁
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !isFinishing) {
                showInactivityDialog()
            }
        }
    }

    private fun showInactivityDialog() {
        inactivityJob?.cancel() // 弹出对话框时停止计时
        if(setLanguageDialog==null){
            setLanguageDialog= SetLanguageDialog(this)
            setLanguageDialog?.setmOnClickListener(object :SetLanguageDialog.onClickListener{
                override fun onNext() {
                    viewModel.saveLanguage()
                }
            })
        }
        setLanguageDialog?.toShow(languageKey)
    }

    private fun setupNativeAd() {
        binding.nativeAdWidget.apply {
            setAdUnitId(AreaKey.languageSetBtmNativeAdv)
            bindToLifecycle(this@SetLanguageActivity)
            startAutoRefresh()
        }
    }


    private fun initAdapter() {
        val languageKeys = viewModel.languageKeys
        adapter = LanguageAdapter(languageKeys)
        binding.rcLanguage.layoutManager = GridLayoutManager(this, 2)
        binding.rcLanguage.adapter = adapter
        adapter.setOnItemClickListener { _, _, position ->
            viewModel.selectLanguage(languageKeys[position])
        }

        binding.tvSave.safeClick {
            viewModel.saveLanguage()
        }
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        inactivityJob?.cancel()  // 页面销毁时取消协程
        setLanguageDialog?.relese()
        setLanguageDialog=null
        super.onDestroy()

    }



  private var languageKey=""


    private fun setupObservers() {
        // 观察选中语言变化，更新 Adapter 高亮
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedLanguageKey.collectLatest { key ->
                    if (key != null) {
                        languageKey=key
                        adapter.setSelectedKey(key)
                    }
                }
            }
        }
        // 观察保存事件，执行语言切换并刷新界面文字
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SetLanguageEvent.SaveLanguage -> {
                            LogUtil.log("settings_language", mapOf("language_value" to languageKey))
                            LocaleHelper.setAppLocale(this@SetLanguageActivity, event.locale)
                            // 刷新因语言切换而变化的文字
                            binding.tvTitle.text = getString(R.string.language)
                            binding.tvSave.text = getString(R.string.save)
                            showInterstitialAdWithStats(AreaKey.newLanguageSetFinishAdv) {
                                if (!AppPrefsUtils.getBoolean(AppConfig.IS_SET_USER)) {
                                    val skipGuide = AppPrefsUtils.getBoolean("skip_guide", false)
                                    val skip_login = AppPrefsUtils.getBoolean("skip_login", false) // 补充登录状态
                                    val target = when {
                                        !skipGuide -> GuideActivity::class.java
                                        !skip_login -> LoginActivity::class.java
                                        else -> MainActivity::class.java
                                    }
                                    if(target==MainActivity::class.java){
                                        AppPrefsUtils.putBoolean(AppConfig.IS_SET_USER,true)
                                    }
                                    readyGoThenKill(target)
                                } else {
                                    ActivityManagerUtils.finishTopActivity(SettingActivity::class.java)
                                    ActivityManagerUtils.finishTopActivity(MainActivity::class.java)
                                    readyGoThenKill(SplashActivity::class.java)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}