package com.findmyphone.gpstracker.ui

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.bean.ShowTrainBean
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.databinding.ActivityShowTrainBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.text.clear

class ShowTrainActivity : BaseViewActivity<ActivityShowTrainBinding>() {

    private val TAG = "ShowTrainActivity"

    private val datas: MutableList<ShowTrainBean> by lazy { createCityList() }
    private val adapter: CityAdapter by lazy { CityAdapter() }

    // 协程任务，用于取消上一个图片加载
    private var loadJob: Job? = null

    override fun initializeActivity() {
        super.initializeActivity()
        initViews()
        setupBackPressHandler()
        // 默认显示纽约
        showCityDetail(datas[9])
    }

    // ---------- 初始化 ----------
    private fun initViews() {
        // 列表
        binding.rcCity.layoutManager = LinearLayoutManager(this)
        binding.rcCity.adapter = adapter

        // 返回按钮
        binding.ivPhotoBack.setOnClickListener { onBack() }
        // 点击大图区域返回列表
        binding.rlCity.setOnClickListener { showCityList() }
    }

    // ---------- 返回键处理：先回列表再退出 ----------
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    private fun onBack() {
        if (binding.rlCity.isVisible) {
            // 当前显示大图，先返回列表
            showCityList()
        } else {
            showInterstitialAdWithStats(AreaKey.metroMapBackHomeAdv) {
                finish()
            }
        }
    }

    // ---------- 视图切换 ----------
    private fun showCityDetail(city: ShowTrainBean) {
        binding.tvCity.text = city.name
        loadCityImage(city.url)

        binding.rcCity.visibility = View.GONE
        binding.rlCity.visibility = View.VISIBLE
        binding.photoView.visibility = View.VISIBLE
    }

    private fun showCityList() {
        binding.rcCity.visibility = View.VISIBLE
        binding.rlCity.visibility = View.GONE
        binding.photoView.visibility = View.GONE
    }

    // ---------- 图片加载（协程 + 4秒超时 + 缩略图 + 缓存）----------
    private fun loadCityImage(url: String) {
        // 取消上一次正在进行的加载
        loadJob?.cancel()
        loadJob = lifecycleScope.launch(Dispatchers.Main) {
            // 仅在图片还没显示时显示 loading，防止切换城市时闪烁
            if (binding.photoView.drawable == null) {
                handleLoadingState(true)
            }

            try {
                withTimeout(5000L) {
                    loadImageWithGlide(url)
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Image load timeout: $url")
            } catch (e: CancellationException) {
                // 重新抛出取消异常，确保协程取消链正常
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image load failed: $url", e)
            } finally {
                handleLoadingState(false)
            }
        }
    }

    /**
     * 将 Glide 回调式加载转换为挂起函数，支持协程取消自动清理。
     */
    private suspend fun loadImageWithGlide(url: String): Drawable =
        suspendCancellableCoroutine { continuation ->
            val target = Glide.with(this@ShowTrainActivity)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.DATA)        // 磁盘缓存原图
                .thumbnail(                                       // 渐进式：先显示低清图
                    Glide.with(this@ShowTrainActivity)
                        .load(url)
                        .override(200, 200)
                )
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>,
                        isFirstResource: Boolean
                    ): Boolean {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                e ?: RuntimeException("Image load failed")
                            )
                        }
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable?>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        if (continuation.isActive) {
                            continuation.resume(resource)
                        }
                        return false
                    }
                })
                .into(binding.photoView)

            // 协程取消时（超时或主动取消），终止 Glide 请求
            continuation.invokeOnCancellation {
                Glide.with(this@ShowTrainActivity).clear(target)
            }
        }



    // ---------- 数据创建 ----------
    private fun createCityList(): MutableList<ShowTrainBean> = mutableListOf(
        ShowTrainBean(getString(R.string.atlanta), AppConfig.TRAIN_URL1),
        ShowTrainBean(getString(R.string.baltimore), AppConfig.TRAIN_URL2),
        ShowTrainBean(getString(R.string.boston), AppConfig.TRAIN_URL3),
        ShowTrainBean(getString(R.string.chicago), AppConfig.TRAIN_URL4),
        ShowTrainBean(getString(R.string.cleveland), AppConfig.TRAIN_URL5),
        ShowTrainBean(getString(R.string.hawaii), AppConfig.TRAIN_URL6),
        ShowTrainBean(getString(R.string.losangeles), AppConfig.TRAIN_URL7),
        ShowTrainBean(getString(R.string.miami), AppConfig.TRAIN_URL8),
        ShowTrainBean(getString(R.string.newjersey), AppConfig.TRAIN_URL9),
        ShowTrainBean(getString(R.string.newyork), AppConfig.TRAIN_URL10),
        ShowTrainBean(getString(R.string.philadelphia), AppConfig.TRAIN_URL11),
        ShowTrainBean(getString(R.string.san_Francisco), AppConfig.TRAIN_URL12),
        ShowTrainBean(getString(R.string.washington), AppConfig.TRAIN_URL13)
    )

    // ---------- 适配器 ----------
    private inner class CityAdapter :
        BaseQuickAdapter<ShowTrainBean, BaseViewHolder>(R.layout.item_train, datas) {

        override fun convert(holder: BaseViewHolder, item: ShowTrainBean) {
            holder.setText(R.id.tv_name, item.name)
        }

        init {
            setOnItemClickListener { _, _, position ->
                showCityDetail(datas[position])
            }
        }
    }

    override fun onDestroy() {
        loadJob?.cancel()   // 双保险，lifecycleScope 会自动取消，但手动取消也无害
        super.onDestroy()
    }
}