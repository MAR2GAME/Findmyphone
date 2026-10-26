package com.findmyphone.gpstracker.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.base.command.readyGo
import com.findmyphone.gpstracker.bean.FriendLocationBeansItem
import com.findmyphone.gpstracker.databinding.ActivityFriendLocationBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.jvm.java

class FriendLocationActivity : BaseViewActivity<ActivityFriendLocationBinding>(),
    OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private var friendMarker: Marker? = null
    private var friendLocationData: FriendLocationBeansItem? = null
    private var customMarkerIcon: BitmapDescriptor? = null
    private var initialLatLng: LatLng? = null
    private var scaledBitmap: android.graphics.Bitmap? = null   // 用于回收

    companion object {
        private const val DEFAULT_ZOOM = 15f
        private const val TAG = "FriendLocationActivity"
        const val EXTRA_FRIEND_LOCATION = "extra_friend_location"
    }


    override fun initializeActivity() {
        super.initializeActivity()
        // 兼容 Android 13+ 的 Parcelable 获取方式
        friendLocationData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_FRIEND_LOCATION, FriendLocationBeansItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_FRIEND_LOCATION) as? FriendLocationBeansItem
        }
        if (friendLocationData == null || friendLocationData?.position == null) {
            Toast.makeText(this, getString(R.string.invalid_location_data), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        initMarkerIcon()
        setupNativeAd()
        binding.ivBack.setOnClickListener { finish() }
        binding.rlSetting.setOnClickListener {
            val bundle = Bundle().apply {
                putParcelable("extra_friend_location", friendLocationData)
            }
            readyGo(DeleteFriendActivity::class.java, bundle)
        }
        if(!TextUtils.isEmpty(friendLocationData!!.displayName)){
            binding.tvName.text=friendLocationData!!.displayName
        }
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    private fun setupNativeAd() {
        binding.nativeAdWidget.apply {
            setLayoutResId(R.layout.view_native_ad_thr,3)
            setAdUnitId(AreaKey.friendMapNativeAdv)
            bindToLifecycle(this@FriendLocationActivity)
            // 启动自动刷新（开始加载并每隔30秒刷新一次）
            startAutoRefresh()
        }
    }

    private fun initMarkerIcon() {
        if (customMarkerIcon == null) {
            val originalBitmap = android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_map_loc)
            if (originalBitmap == null) return
            val targetWidth = 48.dpToPx()
            val targetHeight = 48.dpToPx()
            scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, false)
            customMarkerIcon = BitmapDescriptorFactory.fromBitmap(scaledBitmap!!)
            originalBitmap.recycle() // 原始 bitmap 立即回收
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = false
        setupInfoWindowAdapter()

        mMap.setOnInfoWindowClickListener { marker ->
            val lat = marker.position.latitude
            val lng = marker.position.longitude
            val address = marker.title ?: "目标位置"
            // 对地址进行 URL 编码，防止空格、中文等特殊字符导致解析失败
            val encodedAddress = Uri.encode(address)

            // 使用 Directions API 的 URL，不指定起点则默认为"我的位置"
            val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&destinationlabel=$encodedAddress")

            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                // 未安装 Google Maps 应用，使用浏览器打开
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }

        mMap.setOnInfoWindowLongClickListener { marker ->
            // 获取地址：优先使用 snippet，如果为 null 则尝试 title，再不行就 fallback 经纬度
            val address = marker.snippet?.takeIf { it.isNotBlank() }
                ?: marker.title?.takeIf { it.isNotBlank() }
                ?: "(${marker.position.latitude}, ${marker.position.longitude})"

            // 复制到剪贴板
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", address)
            clipboard.setPrimaryClip(clip)
        }

        val position = friendLocationData?.position
        if (position != null && position.latitude != 0.0 && position.longitude != 0.0) {
            val latLng = LatLng(position.latitude, position.longitude)
            initialLatLng = latLng
            addMarkerAndMoveCamera(latLng)
        } else {
            Toast.makeText(this, getString(R.string.invalid_coordinates), Toast.LENGTH_SHORT).show()
        }

        // 回到初始位置按钮
        binding.rlMove.setOnClickListener {
            initialLatLng?.let {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, DEFAULT_ZOOM))
            } ?: run {
                Toast.makeText(this, getString(R.string.no_initial_location), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addMarkerAndMoveCamera(latLng: LatLng) {
        val displayName = friendLocationData?.displayName ?: getString(R.string.friend_location)

        friendMarker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(displayName)
                .snippet(getString(R.string.loading_address))
                .icon(customMarkerIcon)
        )

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
        friendMarker?.showInfoWindow()

        // 异步加载详细地址，并更新 InfoWindow
        lifecycleScope.launch {
            // 只在 Activity 处于 STARTED 或 RESUMED 状态时执行 UI 更新
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val address = getAddressWithGeocoder(latLng.latitude, latLng.longitude)
                val finalSnippet = if (address.isNotEmpty()) address else getString(R.string.no_address)
                friendMarker?.snippet = finalSnippet
                if (friendMarker?.isInfoWindowShown == true) {
                    friendMarker?.hideInfoWindow()
                    friendMarker?.showInfoWindow()
                }
            }
        }
    }

    /**
     * 获取地址（完全兼容 MapFragment 的实现逻辑）
     * - Android 13+ (API 33+) 使用异步 GeocodeListener
     * - 低版本使用同步 API（在 IO 线程安全执行）
     */
    private suspend fun getAddressWithGeocoder(latitude: Double, longitude: Double): String =
        withContext(Dispatchers.IO) {
            if (!isNetworkAvailable()) return@withContext ""

            try {
                val geocoder = Geocoder(this@FriendLocationActivity, Locale.getDefault())
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getAddressAsync(geocoder, latitude, longitude)
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.getAddressLine(0)
                }
                address ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "Geocoder failed", e)
                ""
            }
        }

    /**
     * 异步 Geocoder 实现（仅用于 API 33+）
     * 已添加 Activity 生命周期检查，避免在销毁后回调导致崩溃
     */
    private suspend fun getAddressAsync(geocoder: Geocoder, latitude: Double, longitude: Double): String? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) {
                            continuation.resume(addresses.firstOrNull()?.getAddressLine(0) ?: "")
                        }
                    }

                    override fun onError(errorMessage: String?) {

                    }
                })
            } else {
                continuation.resume(null)
            }
        }



    /**
     * 自定义 InfoWindow 适配器
     */
    private fun setupInfoWindowAdapter() {
        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            @SuppressLint("MissingInflatedId")
            override fun getInfoWindow(marker: Marker): View? {
                return try {
                    val view = LayoutInflater.from(this@FriendLocationActivity).inflate(R.layout.pop_friend_location, null, false)
                    val tvLoc = view.findViewById<TextView>(R.id.tv_loc)
                    tvLoc.text = marker.snippet ?: getString(R.string.no_address)
                    view
                } catch (e: Exception) {
                    null
                }
            }

            override fun getInfoContents(marker: Marker): View? = null
        })
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing ACCESS_NETWORK_STATE permission", e)
            false
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 回收 Bitmap 内存
        scaledBitmap?.recycle()
        scaledBitmap = null
        customMarkerIcon = null
        friendMarker?.remove()
        friendMarker = null
    }
}