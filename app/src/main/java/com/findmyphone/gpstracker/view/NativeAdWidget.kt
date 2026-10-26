package com.findmyphone.gpstracker.view

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.facebook.shimmer.ShimmerFrameLayout
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.ShowNativeAd
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

import kotlinx.coroutines.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.apply
import kotlin.collections.isNotEmpty
import kotlin.takeIf

class NativeAdWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr), DefaultLifecycleObserver {

    companion object {
        private const val TAG = "NativeAdWidget"
    }

    private var shimmerLayout: ShimmerFrameLayout? = null
    private var nativeAdView: NativeAdView? = null

    @Volatile
    private var nativeAd: NativeAd? = null
    private var refreshJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null

    @Volatile
    private var isRefreshing = false
    private var isBound = false
    private var isDestroyed = false
    private var adUnitId: String = "coreFeaturesNativeAdv"

    private var type: Int = 1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val isLoading = AtomicBoolean(false)
    private var lastLoadSuccessTime = 0L

    @Volatile
    private var autoRefreshEnabled = false
    private val consecutiveFailures = AtomicInteger(0)

    /** 连续失败多少次后彻底隐藏控件并暂停当前前台刷新 */
    var maxConsecutiveFailures: Int = 2

    var refreshIntervalSeconds: Long = AppPrefsUtils.getLong("native_refresh_time", 30)
        set(value) {
            field = value
        }

    init {
        setLayoutResId(R.layout.view_native_ad_one, 1)
    }

    fun setLayoutResId(@LayoutRes layoutResId: Int, type: Int) {
        if (isDestroyed) return
        // 切换布局前，彻底停止当前刷新，销毁广告
        stopRefreshAndReleaseAd()
        nativeAd = null
        removeAllViews()
        this.type = type
        LayoutInflater.from(context).inflate(layoutResId, this, true)
        shimmerLayout = findViewById(R.id.shimmer_layout)
        nativeAdView = findViewById(R.id.native_ad_view)

        val missingIds = mutableListOf<String>()
        if (shimmerLayout == null) missingIds.add("shimmer_layout")
        if (nativeAdView == null) missingIds.add("native_ad_view")
        if (missingIds.isNotEmpty()) {
            throw kotlin.IllegalArgumentException("NativeAdWidget: 布局缺少必要控件: $missingIds")
        }

        shimmerLayout?.apply {
            visibility = VISIBLE
            stopShimmer()
        }
        nativeAdView?.visibility = GONE
        visibility = VISIBLE
    }

    fun setAdUnitId(unitId: String) {
        this.adUnitId = unitId
    }

    fun bindToLifecycle(owner: LifecycleOwner) {
        if (isBound || isDestroyed) return
        this.lifecycleOwner = owner
        owner.lifecycle.addObserver(this)
        isBound = true
    }

    fun startAutoRefresh() {
        if (isDestroyed) return
        autoRefreshEnabled = true
        isRefreshing = true
        consecutiveFailures.set(0)  // 重置计数
        post {
            if (isAttachedToWindow) {
                startRefreshLoop(immediateLoad = true)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshEnabled = false
        stopRefreshAndReleaseAd()
    }

    fun loadAd() {
        if (isDestroyed) return
        scope.launch {
            loadNativeAd()
        }
    }

    private fun stopRefreshAndReleaseAd() {
        isRefreshing = false
        refreshJob?.cancel()
        refreshJob = null
        nativeAd?.destroy()
        nativeAd = null
    }

    private fun startRefreshLoop(immediateLoad: Boolean = true) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (immediateLoad) loadNativeAd()
            while (isRefreshing && !isDestroyed) {
                delay(refreshIntervalSeconds * 1000)
                if (isRefreshing && !isDestroyed) loadNativeAd()
            }
        }
    }

    private suspend fun loadNativeAd() {
        if (!isLoading.compareAndSet(false, true)) return
        try {
            withContext(Dispatchers.Main) {
                if (isDestroyed || !isAttachedToWindow) return@withContext
                showLoading()
            }
            val activity = getValidActivity()
            if (activity == null) {
                Log.w(TAG, "loadNativeAd: no valid Activity")
                withContext(Dispatchers.Main) { handleLoadFailure() }
                return
            }
            val ad = withTimeoutOrNull(8000) {
                ShowNativeAd.getNativeAd(activity, adUnitId)
            }
            withContext(Dispatchers.Main) {
                if (isDestroyed || !isAttachedToWindow) return@withContext
                if (ad == null) {
                    handleLoadFailure()
                } else {
                    consecutiveFailures.set(0)
                    displayAd(ad)
                    lastLoadSuccessTime = System.currentTimeMillis()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "loadNativeAd error", e)
            withContext(Dispatchers.Main) {
                if (!isDestroyed && isAttachedToWindow) handleLoadFailure()
            }
        } finally {
            isLoading.set(false)
        }
    }

    private fun handleLoadFailure() {
        if (isDestroyed || !isAttachedToWindow) return
        val fails = consecutiveFailures.incrementAndGet()
        if (fails >= maxConsecutiveFailures) {
            stopCurrentRefreshCycle()
            hideAdCompletely()
        } else {
            hideAdContentKeepVisible()
        }
    }

    private fun getValidActivity(): Activity? {
        // 优先从 context 链中寻找最近的 Activity，避免跨 Activity 问题
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx.takeIf { !it.isFinishing && !it.isDestroyed }
            ctx = ctx.baseContext
        }
        return null
    }

    private fun showLoading() {
        if (isDestroyed) return
        // 只在确实需要显示时才恢复可见性
        if (visibility != VISIBLE) visibility = VISIBLE
        shimmerLayout?.apply {
            visibility = VISIBLE
            startShimmer()
        }
        nativeAdView?.visibility = GONE
    }

    private fun hideAdContentKeepVisible() {
        if (!isAttachedToWindow || isDestroyed) return
        shimmerLayout?.stopShimmer()
        shimmerLayout?.visibility = GONE
        nativeAdView?.visibility = GONE
    }

    private fun hideAdCompletely() {
        if (!isAttachedToWindow || isDestroyed) return
        shimmerLayout?.stopShimmer()
        nativeAdView?.visibility = GONE
        shimmerLayout?.visibility = GONE
        visibility = GONE
    }

    private fun stopCurrentRefreshCycle() {
        refreshJob?.cancel()
        refreshJob = null
        isRefreshing = false
    }

    private fun displayAd(ad: NativeAd) {
        if (!isAttachedToWindow || isDestroyed) return
        val adView = nativeAdView ?: return
        nativeAd?.destroy()
        nativeAd = ad
        shimmerLayout?.stopShimmer()
        shimmerLayout?.visibility = GONE
        adView.visibility = VISIBLE
        visibility = VISIBLE
        populateNativeAdView(ad, adView)
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        val callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)
        val adChoicesView = adView.findViewById<AdChoicesView>(R.id.ad_choices)
        adView.headlineView = headlineView
        adView.callToActionView = callToActionView
        if (adChoicesView != null) {
            adView.adChoicesView = adChoicesView
        }
        headlineView.text = nativeAd.headline
        if (nativeAd.callToAction != null) {
            callToActionView.text = nativeAd.callToAction
            callToActionView.visibility = VISIBLE
        } else {
            callToActionView.visibility = GONE
        }
        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        adView.bodyView = bodyView
        if (nativeAd.body != null) {
            bodyView?.text = nativeAd.body
            bodyView?.visibility = VISIBLE
        } else {
            bodyView?.visibility = GONE
        }
        when (type) {
            1 -> {
                val iconView = adView.findViewById<ImageView>(R.id.ad_icon)
                adView.iconView = iconView
                if (nativeAd.icon != null) {
                    iconView.setImageDrawable(nativeAd.icon?.drawable)
                    iconView.visibility = VISIBLE
                } else {
                    iconView.visibility = GONE
                }
                val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
                val mediaContent = nativeAd.mediaContent
                if (mediaContent != null) {
                    mediaView.visibility = VISIBLE
                    adView.mediaView = mediaView
                    mediaView.mediaContent = mediaContent
                } else {
                    mediaView.visibility = GONE
                    adView.mediaView = null
                }
            }
            2 -> {
                val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
                val mediaContent = nativeAd.mediaContent
                if (mediaContent != null) {
                    mediaView.visibility = VISIBLE
                    adView.mediaView = mediaView
                    mediaView.mediaContent = mediaContent
                } else {
                    mediaView.visibility = GONE
                    adView.mediaView = null
                }
            }
            3 -> {
                val iconView = adView.findViewById<ImageView>(R.id.ad_icon)
                adView.iconView = iconView
                if (nativeAd.icon != null) {
                    iconView.setImageDrawable(nativeAd.icon?.drawable)
                    iconView.visibility = VISIBLE
                } else {
                    iconView.visibility = GONE
                }
            }
        }
        adView.setNativeAd(nativeAd)
    }

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        stopAutoRefresh()
        scope.cancel()
        nativeAd?.destroy()
        nativeAd = null
        if (isBound) {
            lifecycleOwner?.lifecycle?.removeObserver(this)
            lifecycleOwner = null
            isBound = false
        }
    }

    // ---- Lifecycle ----
    override fun onResume(owner: LifecycleOwner) {
        if (isDestroyed || !isAttachedToWindow) return

        consecutiveFailures.set(0)

        if (nativeAd != null && nativeAdView != null) {
            // 有广告直接展示
            visibility = VISIBLE
            nativeAdView?.visibility = VISIBLE
            shimmerLayout?.stopShimmer()
            shimmerLayout?.visibility = GONE
        } else if (autoRefreshEnabled) {
            // 重新开始刷新，但不立即显示加载动画（避免闪烁）
            isRefreshing = true
            if (refreshJob?.isActive != true) {
                startRefreshLoop(immediateLoad = true)
            }
            // 如果当前控件已隐藏，且没有广告，暂时保持隐藏，待 loadNativeAd 成功后再显示
            // showLoading 会在 loadNativeAd 开始时调用，那时再可见
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        if (isDestroyed) return
        refreshJob?.cancel()
        refreshJob = null
        isRefreshing = false
    }

    override fun onDestroy(owner: LifecycleOwner) {
        destroy()
    }

    override fun onDetachedFromWindow() {
        refreshJob?.cancel()
        refreshJob = null
        isRefreshing = false
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 只在自动刷新开启且之前没有因为连续失败而彻底隐藏时才立即重启
        if (!isDestroyed && autoRefreshEnabled && refreshJob?.isActive != true) {
            // 如果之前控件是 GONE (因为失败隐藏)，这里不强制显示，交给 loadNativeAd 成功后再显示
            isRefreshing = true
            startRefreshLoop(immediateLoad = true)
        }
    }
}