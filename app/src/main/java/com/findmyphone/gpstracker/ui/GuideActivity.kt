package com.findmyphone.gpstracker.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AdvCheckManager
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.base.command.dp2px
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.bean.GuideItem
import com.findmyphone.gpstracker.bean.NativeAdClickEvent
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.databinding.ActivityGuideBinding
import com.findmyphone.gpstracker.services.PersistentNotificationService
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.utils.NativeAdConfigManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.compareTo

class GuideActivity : BaseViewActivity<ActivityGuideBinding>() {

    companion object {
        private const val TAG = "GuideActivity"
        private const val EXTRA_TYPE = "type"

        // 指示器常量
        private const val INDICATOR_CORNER_RADIUS_DP = 4
        private const val INDICATOR_WIDTH_DP = 20
        private const val INDICATOR_HEIGHT_DP = 6
        private const val INDICATOR_MARGIN_DP = 4

        // 脉冲动画常量
        private const val PULSE_SCALE_START = 1.0f
        private const val PULSE_SCALE_PEAK = 1.15f
        private const val PULSE_DURATION_MS = 800L
    }

    private lateinit var guideAdapter: BaseQuickAdapter<GuideItem, BaseViewHolder>

    // 指示器背景（按需创建）
    private lateinit var selectedIndicatorBg: GradientDrawable
    private lateinit var normalIndicatorBg: GradientDrawable

    private var pulseAnimator: ObjectAnimator? = null

    // 预计算 px 尺寸
    private val indicatorWidthPx by lazy { dp2px(this, INDICATOR_WIDTH_DP) }
    private val indicatorHeightPx by lazy { dp2px(this, INDICATOR_HEIGHT_DP) }
    private val indicatorMarginPx by lazy { dp2px(this, INDICATOR_MARGIN_DP) }
    private val indicatorCornerRadiusPx by lazy { dp2px(this, INDICATOR_CORNER_RADIUS_DP).toFloat() }

    private val guideItems by lazy { createGuideItems() }

    override fun initializeActivity() {
        super.initializeActivity()
        initViews()
        initIndicatorDrawables()
        setupViewPager()
        setupIndicators()
        setupNativeAd()
        setupBackPressHandler()
        EventBus.getDefault().register(this)
        LogUtil.log("enter_guide_page", emptyMap())
    }

    private var clickSize=0
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: NativeAdClickEvent) {
        if(NativeAdConfigManager.containsAreaKey(AreaKey.introPageBtmNativeAdv)){
            clickSize++
            if(clickSize>= NativeAdConfigManager.getClickSize(AreaKey.introPageBtmNativeAdv)){
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
            }
        })
    }

    private fun initIndicatorDrawables() {
        selectedIndicatorBg = createIndicatorDrawable(true)
        normalIndicatorBg = createIndicatorDrawable(false)
    }

    override fun onResume() {
        super.onResume()
        startPulseAnimationIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        stopPulseAnimation()
    }

    override fun onDestroy() {
        stopPulseAnimation()
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    private fun initViews() {
        binding.btNext.setOnClickListener { handleNextButtonClick() }
    }

    private fun setupViewPager() {
        guideAdapter = createGuideAdapter()
        with(binding.viewPager) {
            adapter = guideAdapter
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateIndicators(position)
                    updateHintText(position)
                }
            })
        }
        guideAdapter.setList(guideItems)
    }

    private fun setupIndicators() {
        with(binding.indicatorLayout) {
            removeAllViews()
            repeat(guideItems.size) {
                addView(createIndicatorView())
            }
        }
        updateIndicators(0)
    }

    private fun createIndicatorView(): View {
        return View(this).apply {
            background = normalIndicatorBg  // 默认未选中
            layoutParams = LinearLayout.LayoutParams(indicatorWidthPx, indicatorHeightPx).apply {
                setMargins(indicatorMarginPx, 0, indicatorMarginPx, 0)
            }
        }
    }

    private fun updateIndicators(position: Int) {
        for (i in 0 until binding.indicatorLayout.childCount) {
            val indicator = binding.indicatorLayout.getChildAt(i)
            val bg = if (i == position) selectedIndicatorBg else normalIndicatorBg
            // 直接修改背景，避免重复 setBackground 带来的重绘（可选优化）
            if (indicator.background != bg) {
                indicator.background = bg
            }
        }
    }

    private fun createIndicatorDrawable(isSelected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = indicatorCornerRadiusPx
            setColor(
                ContextCompat.getColor(
                    this@GuideActivity,
                    if (isSelected) R.color.theme_color else R.color.indicator_nor
                )
            )
        }
    }

    private fun updateHintText(position: Int) {
        val isLastPage = position == guideItems.lastIndex
        binding.tvHint.setText(if (isLastPage) R.string.guide_hint2 else R.string.guide_hint1)
        binding.btNext.setText(if (isLastPage) R.string.start else R.string.next)
    }

    private fun createGuideAdapter(): BaseQuickAdapter<GuideItem, BaseViewHolder> {
        return object : BaseQuickAdapter<GuideItem, BaseViewHolder>(R.layout.item_guide) {
            override fun convert(holder: BaseViewHolder, item: GuideItem) {
                Glide.with(this@GuideActivity)   // 绑定 Activity 生命周期
                    .load(item.imageRes)
                    .into(holder.getView(R.id.ivGuide))
            }
        }
    }

    private fun createGuideItems(): List<GuideItem> {
        return listOf(
            GuideItem(R.mipmap.guide_bg1),
            GuideItem(R.mipmap.guide_bg2)
        )
    }

    private fun handleNextButtonClick() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition < guideItems.lastIndex) {
            LogUtil.log("app_presentation", mapOf("page" to "0"))
            binding.viewPager.currentItem = currentPosition + 1
        } else {
            LogUtil.log("app_presentation", mapOf("page" to "1"))
            val isLogin = AppPrefsUtils.getBoolean("skip_login", false)
            if(isLogin){
                if(AdvCheckManager.params.fromNature){
                    AppPrefsUtils.commitBoolean(AppConfig.IS_SET_USER, true)
                }else{
                    if (AppPrefsUtils.getBoolean("is_First_to_guide", true)) {
                        AppPrefsUtils.commitBoolean("is_First_to_guide", false)
                    } else {
                        AppPrefsUtils.commitBoolean(AppConfig.IS_SET_USER, true)
                    }
                }
                showInterstitialAdWithStats(AreaKey.newIntroPageFinishAdv) {
                    readyGoThenKill(MainActivity::class.java)
                }
            }else{
                showInterstitialAdWithStats(AreaKey.newIntroPageFinishAdv) {
                    val bundle = Bundle().apply { putInt("type", 0) }
                    readyGoThenKill(LoginActivity::class.java, bundle)
                }
            }
        }
    }

    private fun setupNativeAd() {
        binding.nativeAdWidget.visibility = View.VISIBLE
        binding.nativeAdWidget.apply {
            setLayoutResId(R.layout.view_native_ad_two, 2)
            setAdUnitId(AreaKey.introPageBtmNativeAdv)
            bindToLifecycle(this@GuideActivity)
            startAutoRefresh()
        }
    }

    // ---------- 脉冲动画 ----------
    private fun startPulseAnimation() {
        val view = binding.btNext
        if (view.visibility != View.VISIBLE) return

        stopPulseAnimation()  // 取消旧动画并恢复缩放
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat("scaleX", PULSE_SCALE_START, PULSE_SCALE_PEAK, PULSE_SCALE_START),
            PropertyValuesHolder.ofFloat("scaleY", PULSE_SCALE_START, PULSE_SCALE_PEAK, PULSE_SCALE_START)
        ).apply {
            duration = PULSE_DURATION_MS
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.btNext.scaleX = PULSE_SCALE_START
        binding.btNext.scaleY = PULSE_SCALE_START
    }

    private fun startPulseAnimationIfNeeded() {
        if (!isFinishing && !isDestroyed && binding.btNext.visibility == View.VISIBLE) {
            startPulseAnimation()
        }
    }
}