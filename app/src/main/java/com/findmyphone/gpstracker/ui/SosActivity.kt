package com.findmyphone.gpstracker.ui

import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.databinding.ActivitySosBinding
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.view.RippleView
import com.findmyphone.gpstracker.viewmodel.SosActivityViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.compareTo
import kotlin.div

import kotlin.math.max
import kotlin.toString

class SosActivity : BaseMvvmActivity<ActivitySosBinding, SosActivityViewModel>() {

    private lateinit var rippleView: RippleView
    private var countdownJob: Job? = null
    private var rippleJob: Job? = null
    private var layoutChangeListener: View.OnLayoutChangeListener? = null

    private var msg: String = ""

    override fun init() {
        val nickname = AppPrefsUtils.getString(AppConfig.NICK_NAME) ?: ""
        val defaultMsg = getString(R.string.sos_message)
        val address = intent.getStringExtra("address") ?: ""
        msg = "$nickname$defaultMsg$address"

        setupRippleView()
        observeViewModel()
        startCountdownAndRipple()
        setupBackPressHandler()
    }

    private fun setupRippleView() {
        rippleView = RippleView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
        }
        (binding.root as? RelativeLayout)?.addView(rippleView, 0)

        layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateRippleParams()
        }
        binding.root.addOnLayoutChangeListener(layoutChangeListener)
        binding.root.doOnPreDraw { updateRippleParams() }
    }

    private fun updateRippleParams() {
        val tvTime = binding.tvTime
        val tvHint = binding.tvHint
        if (tvTime.width <= 0 || tvHint.width <= 0) return

        val timeLocation = IntArray(2)
        val rootLocation = IntArray(2)
        tvTime.getLocationInWindow(timeLocation)
        binding.root.getLocationInWindow(rootLocation)
        val centerX = timeLocation[0] - rootLocation[0] + tvTime.width / 2f
        val centerY = timeLocation[1] - rootLocation[1] + tvTime.height / 2f
        val timeRadius = tvTime.width / 2f

        val hintLocation = IntArray(2)
        tvHint.getLocationInWindow(hintLocation)
        val hintLeft = hintLocation[0] - rootLocation[0]
        val hintRight = hintLeft + tvHint.width
        val hintBottom = hintLocation[1] - rootLocation[1] + tvHint.height

        val maxRadiusHorizontal = minOf(centerX - hintLeft, hintRight - centerX)
        val maxRadiusVertical = centerY - hintBottom
        var maxRadius = minOf(maxRadiusHorizontal, maxRadiusVertical)
        maxRadius = max(maxRadius, timeRadius + 2f)

        rippleView.setCenter(centerX, centerY)
        rippleView.setRadiusLimit(timeRadius, maxRadius)
    }

    private fun observeViewModel() {
        viewModel.remainingSeconds.observe(this) { seconds ->
            binding.tvTime.text = seconds.toString()
            when (seconds) {
                9, 6, 3 -> viewModel.senSos(msg)
                0 -> onBack()
            }
        }
    }

    private fun startCountdownAndRipple() {
        countdownJob?.cancel()
        rippleJob?.cancel()

        countdownJob = lifecycleScope.launch {
            try {
                var seconds = viewModel.remainingSeconds.value ?: 10
                while (seconds > 0 && isActive && viewModel.isCountingDown.value == true) {
                    delay(1000)
                    seconds--
                    viewModel.decrement()
                }
                if (seconds == 0 && isActive) {
                    // 倒计时结束，安全停止波纹
                    stopAllRippleTasks()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        rippleJob = lifecycleScope.launch {
            try {
                while (isActive && (viewModel.remainingSeconds.value ?: 0) > 0) {
                    rippleView.addRipple(duration = 1500L)
                    delay(800L)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* 拦截返回键 */ }
        })

        binding.btCancel.setOnClickListener {
            onBack()
        }
    }

    // ---------- 核心：安全停止波纹并退出 ----------
    private fun onBack() {
        // 1. 先停止所有波纹相关任务（协程、动画）
        stopAllRippleTasks()
        // 2. 清理波纹视图，确保在 finish 前没有残留动画
        clearRipplesSafely()
        // 3. 移除布局监听，防止内存泄漏
        layoutChangeListener?.let { binding.root.removeOnLayoutChangeListener(it) }
        // 4. 展示广告后关闭
        showInterstitialAdWithStats(AreaKey.sosBackHomeAdv) {
            finish()
        }
    }

    /**
     * 停止所有可能修改波纹列表的任务：
     * - 取消倒计时协程
     * - 取消波纹生成协程
     * - 停掉 RippleView 内部所有动画 (如果 RippleView 提供了方法)
     */
    private fun stopAllRippleTasks() {
        countdownJob?.cancel()
        rippleJob?.cancel()
        // 如果 RippleView 有停止动画的方法，可在此调用，例如：
        // rippleView.stopAllAnimations()
    }

    /**
     * 安全清除波纹：将清除操作 post 到主线程消息队列末尾，
     * 保证所有正在执行的 addRipple 及动画回调已结束。
     */
    private fun clearRipplesSafely() {
        if (binding.root.isAttachedToWindow) {
            binding.root.post {
                try {
                    rippleView.clearAllRipples()
                } catch (e: ConcurrentModificationException) {
                    // 极端情况兜底，忽略
                    e.printStackTrace()
                }
            }
        } else {
            // 如果视图已脱离窗口，直接尝试清除
            try {
                rippleView.clearAllRipples()
            } catch (e: ConcurrentModificationException) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        // onDestroy 仅做最后的兜底清理，不再重复 post 清理（已在 onBack 中处理）
        stopAllRippleTasks()
        super.onDestroy()
    }
}