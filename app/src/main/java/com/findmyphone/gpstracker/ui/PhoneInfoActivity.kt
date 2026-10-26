package com.findmyphone.gpstracker.ui

import android.annotation.SuppressLint
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.bean.InfoItem
import com.findmyphone.gpstracker.bean.PhoneNumberInfo
import com.findmyphone.gpstracker.bean.ScanInfo
import com.findmyphone.gpstracker.databinding.ActivityPhoneInfoBinding
import com.findmyphone.gpstracker.view.PhoneInfoDialog
import com.findmyphone.gpstracker.viewmodel.PhoneInfoViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class PhoneInfoActivity : BaseMvvmActivity<ActivityPhoneInfoBinding, PhoneInfoViewModel>(), OnMapReadyCallback {
    private var currentProgress = 0
    private lateinit var appAdapter: BaseQuickAdapter<ScanInfo, BaseViewHolder>
    private lateinit var mMap: GoogleMap
    private var infoList: MutableList<ScanInfo> = mutableListOf()
    private var targetAddress: String? = null   // 用于地理编码的目标地址
    private  lateinit  var mapFragment:SupportMapFragment
    private var number: String=""
    private   var phonInfoData: MutableList<InfoItem> = mutableListOf()

    private   var phoneNumberInfo: PhoneNumberInfo?=null
    private var phoneNumberInfoDialog: PhoneInfoDialog?=null
    companion object {
        private const val PROGRESS_UPDATE_INTERVAL = 30L
        private const val TOTAL_PROGRESS = 100
    }

    fun onBack(){
        showInterstitialAdWithStats(AreaKey.phoneSearchBackHomeAdv) {
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
        infoList.add(ScanInfo(getString(R.string.connecting_to_station), false))
        infoList.add(ScanInfo(getString(R.string.identifying_the_network_operator), false))
        infoList.add(ScanInfo(getString(R.string.connecting_to_the_phone), false))
        appAdapter = object : BaseQuickAdapter<ScanInfo, BaseViewHolder>(R.layout.item_search_info) {
            override fun convert(holder: BaseViewHolder, item: ScanInfo) {
                item.let {
                    holder.setText(R.id.tv_info, it.tagName)
                    holder.setGone(R.id.iv_check, !it.isCheck)
                    holder.setGone(R.id.rotatingRingView, it.isCheck)
                }
            }
        }
        binding.rc.adapter = appAdapter
        binding.rc.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        appAdapter.setList(infoList)
        // 列表项状态更新（动态更新前 N 项，避免硬编码索引）
        lifecycleScope.launch {
            for (i in 0 until infoList.size) {
                delay(1000L)
                // 将索引 <= i 的项设置为已完成
                for (j in 0..i) {
                    if (j < appAdapter.data.size && !appAdapter.data[j].isCheck) {
                        appAdapter.data[j].isCheck = true
                        appAdapter.notifyItemChanged(j)
                    }
                }
            }
        }
        // 进度条模拟（总时长 = TOTAL_PROGRESS * PROGRESS_UPDATE_INTERVAL = 3000ms）
        lifecycleScope.launch {
            simulateProgress()
        }
    }
    private suspend fun simulateProgress() {
        for (progress in currentProgress until TOTAL_PROGRESS) {
            updateProgress(progress + 1)
            delay(PROGRESS_UPDATE_INTERVAL)
        }
        phoneNumberInfoDialog!!.toShow(number,phonInfoData)
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgress(progress: Int) {
        currentProgress = progress
        binding.progressBar.progress = progress
    }

    // ========== 地图相关 ==========

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        // 先展示世界地图（坐标0,0，缩放级别2）
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(0.0, 0.0), 2f))
        // 解析目标地址并执行3秒滑动动画
        lifecycleScope.launch {
            val targetLatLng = getLatLngFromAddress(targetAddress)
            targetLatLng?.let {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 10f), 2800, null)
            } ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PhoneInfoActivity, getString(R.string.shows_US_region), Toast.LENGTH_SHORT).show()
                }
                val fallbackLatLng = LatLng(39.8283, -98.5795)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(fallbackLatLng, 5f), 2800, null)
            }
        }
    }

    // 地理编码：将地址字符串转换为经纬度（IO线程）
    private suspend fun getLatLngFromAddress(address: String?): LatLng? = withContext(Dispatchers.IO) {
        if (address.isNullOrBlank()) return@withContext null
        return@withContext try {
            val geocoder = Geocoder(this@PhoneInfoActivity, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(address, 1)
            if (!addresses.isNullOrEmpty()) {
                val location = addresses[0]
                LatLng(location.latitude, location.longitude)
            } else {
                // 记录日志，方便排查
                android.util.Log.w("PhoneInfoActivity", "Geocoder returned no result for address: $address")
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("PhoneInfoActivity", "Geocoder failed", e)
            null
        }
    }
    // ========== 地图生命周期管理 ==========
    // 确保 SupportMapFragment 的生命周期与 Activity 同步
    override fun onResume() {
        super.onResume()
        mapFragment.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapFragment.onPause()
    }

    override fun onDestroy() {
        phoneNumberInfoDialog?.release()
        phoneNumberInfoDialog=null
        super.onDestroy()

    }
    override fun init() {
        // 设置进度条最大值（防止布局未设置）
        binding.progressBar.max = TOTAL_PROGRESS
        phoneNumberInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("PhoneNumberInfo", PhoneNumberInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("PhoneNumberInfo") as? PhoneNumberInfo
        }
        if (phoneNumberInfo == null) {
            Toast.makeText(this, getString(R.string.invalid_phone), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        var carrier=""
        var country=""
        var timezone=""
        var city=""
        if(!TextUtils.isEmpty(phoneNumberInfo!!.carrier)){
            carrier=phoneNumberInfo!!.carrier
        }
        if(!TextUtils.isEmpty(phoneNumberInfo!!.countryName)){
            country=phoneNumberInfo!!.countryName
        }
        if(!TextUtils.isEmpty(phoneNumberInfo!!.timezone)){
            timezone=phoneNumberInfo!!.timezone
        }
        if(!TextUtils.isEmpty(phoneNumberInfo!!.city)){
            city=phoneNumberInfo!!.city
        }
        // 构建用于地图定位的地址字符串（国家 + 城市，增加分隔符提高解析准确性）
        targetAddress = buildString {
            phoneNumberInfo!!.countryName?.let { append(it); append(" ") }
            phoneNumberInfo!!.city?.let { append(it) }
        }.trim().takeIf { it.isNotBlank() } ?: phoneNumberInfo!!.countryName
        mapFragment = supportFragmentManager.findFragmentById(R.id.info_map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        phonInfoData.add(InfoItem(getString(R.string.carrier), carrier))
        phonInfoData.add(InfoItem(getString(R.string.country), country))
        phonInfoData.add(InfoItem(getString(R.string.timezone), timezone))
        phonInfoData.add(InfoItem(getString(R.string.city), city))
        phoneNumberInfoDialog= PhoneInfoDialog(this)
        phoneNumberInfoDialog!!.setmOnClickListener(object : PhoneInfoDialog.onClickListener{
            override fun onNext() {
                onBack()
            }
        })
        //   phonInfoData.add(InfoItem(getString(R.string.location),getString(R.string.verified)))
        // 显示电话号码
        if (TextUtils.isEmpty(phoneNumberInfo!!.internationalFormat)) {
            binding.tvPhone.text = phoneNumberInfo!!.originalNumber
            number= phoneNumberInfo!!.originalNumber
        } else {
            binding.tvPhone.text = phoneNumberInfo!!.internationalFormat
            number= phoneNumberInfo!!.internationalFormat
        }
        binding.ivBack.setOnClickListener {
            onBack()
        }
        // 异步加载地图
        //
        initAdapter()
        setupBackPressHandler()
    }
}