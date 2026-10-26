package com.findmyphone.gpstracker.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.bean.PublicServiceItem
import com.findmyphone.gpstracker.databinding.ActivityNearbyPlacesBinding

class NearbyPlacesActivity : BaseViewActivity<ActivityNearbyPlacesBinding>() {
    private lateinit var appAdapter: BaseQuickAdapter<PublicServiceItem, BaseViewHolder>
    // 用于防止重复初始化列表数据
    private var isAdapterInitialized = false
    override fun initializeActivity() {
        super.initializeActivity()
        appOpenFrom = intent.getStringExtra("appOpenFrom")
        initAdapter()
        setupNativeAd()
        setupBackPressHandler()
        setupClickListeners()
    }
    private fun setupClickListeners() {
        binding.run {
            rlCoffee.setOnClickListener { launchGoogleMapsSearch(getString(R.string.coffee)) }
            lrRestaurants.setOnClickListener { launchGoogleMapsSearch(getString(R.string.restaurants)) }
            rlBars.setOnClickListener { launchGoogleMapsSearch(getString(R.string.bars)) }
            ivBack.setOnClickListener { onBack()}
        }
    }

    private fun setupNativeAd() {
        binding.nativeAdWidget.apply {
            setLayoutResId(R.layout.view_native_ad_two,2)
            setAdUnitId(AreaKey.nearbyRestaurantBtmNativeAdv)
            bindToLifecycle(this@NearbyPlacesActivity)
            // 启动自动刷新（开始加载并每隔30秒刷新一次）
            startAutoRefresh()
        }
    }
    private var  appOpenFrom:String?=null

    fun onBack(){
        if(TextUtils.isEmpty(appOpenFrom)){
            finish()
        }else{
            readyGoThenKill(MainActivity::class.java)
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }
    private fun initAdapter() {
        if (isAdapterInitialized) return
        isAdapterInitialized = true
        // 构建公共服务列表（仅初始化一次）
        val infoList = listOf(
            PublicServiceItem(getString(R.string.house), R.mipmap.ic_house),
            PublicServiceItem(getString(R.string.hotel), R.mipmap.ic_hotel),
            PublicServiceItem(getString(R.string.atm), R.mipmap.ic_atm),
            PublicServiceItem(getString(R.string.spa), R.mipmap.ic_spa)
        )

        appAdapter = object : BaseQuickAdapter<PublicServiceItem, BaseViewHolder>(R.layout.item_public) {
            override fun convert(holder: BaseViewHolder, item: PublicServiceItem) {
                holder.setText(R.id.tv_name, item.name)
                holder.setImageResource(R.id.iv_info, item.res)
                holder.getView<ConstraintLayout>(R.id.rl_content).setOnClickListener {
                    launchGoogleMapsSearch(item.name)
                }
            }
        }

        binding.rcService.apply {
            adapter = appAdapter
            layoutManager = GridLayoutManager(this@NearbyPlacesActivity, GRID_SPAN_COUNT)
        }
        appAdapter.setList(infoList)
    }

    /**
     * 启动 Google 地图并搜索指定地点。
     * 优先尝试直接启动 Google 地图应用；若失败则降级为系统选择器。
     */
    private fun launchGoogleMapsSearch(query: String) {
        val encodedQuery = Uri.encode(query)
        val googleMapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery")).apply {
            setPackage(GOOGLE_MAPS_PACKAGE)
            // 添加标志位，避免创建新的任务栈
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // 先检查是否有可处理的 Activity，避免不必要的异常
        if (googleMapsIntent.resolveActivity(packageManager) != null) {
            try {
                startActivity(googleMapsIntent)
            } catch (e: ActivityNotFoundException) {
                // 理论上不会进入，但保留保护
                showMapsUnavailableToast()
            }
        } else {
            // Google Maps 未安装或不可用，尝试降级方案（不限定包名）
            val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery"))
            if (fallbackIntent.resolveActivity(packageManager) != null) {
                try {
                    startActivity(fallbackIntent)
                } catch (e: ActivityNotFoundException) {
                    showMapsUnavailableToast()
                }
            } else {
                showMapsUnavailableToast()
            }
        }
    }

    private fun showMapsUnavailableToast() {
        Toast.makeText(this, R.string.unable_to_start_Google_Maps, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val GRID_SPAN_COUNT = 2
    }
}