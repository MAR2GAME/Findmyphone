package com.findmyphone.gpstracker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.copyText
import com.findmyphone.gpstracker.base.command.hasLocationPermission
import com.findmyphone.gpstracker.base.command.isNetworkAvailable
import com.findmyphone.gpstracker.base.command.readyGo
import com.findmyphone.gpstracker.base.command.shareText
import com.findmyphone.gpstracker.bean.MapTypeBean
import com.findmyphone.gpstracker.databinding.ActivityLiveLocationBinding
import com.findmyphone.gpstracker.utils.LocationPermissionHelper
import com.findmyphone.gpstracker.utils.LocationReporterManager
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.view.AddFriendDialog
import com.findmyphone.gpstracker.view.MapTypeDialog
import com.findmyphone.gpstracker.view.NoFriendDialog
import com.findmyphone.gpstracker.view.PermissionHintDialog
import com.findmyphone.gpstracker.view.SosDialog
import com.findmyphone.gpstracker.view.UserCodeDialog
import com.findmyphone.gpstracker.viewmodel.LiveLocationViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import java.io.ByteArrayOutputStream
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.code
import kotlin.coroutines.resume
import kotlin.jvm.java

class LiveLocationActivity : BaseMvvmActivity<ActivityLiveLocationBinding, LiveLocationViewModel>(),
    OnMapReadyCallback {

    companion object {
        private const val DEFAULT_ZOOM = 17f
        private const val TAG = "LiveLocationActivity"
        private const val GEOCODE_RETRY_COUNT = 2
        private const val MAX_CACHE_SIZE = 100
        private const val CACHE_EXPIRY_MS = 30 * 60 * 1000L
    }

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationManager: LocationManager

    private var addFriendDialog: AddFriendDialog? = null





    // 最近一次有效的地址（清洗后），用于 SOS 或界面
    private var cachedAddress: String? = null
    // 最后一次成功获取的纯文字地址
    private var lastValidAddress: String? = null
    // 记录获得有效地址时的位置，用于判断旧地址过期
    private var lastValidAddressLocation: Location? = null

    private lateinit var mapFragment: SupportMapFragment
    private var noFriendDialog: NoFriendDialog? = null
    private var sosDialog: SosDialog? = null

    private var isMapReady = false
    private var isLocationUpdatesStarted = false
    private var isCameraMoved = false
    private var lastLocation: Location? = null

    @Volatile
    private var myMarker: Marker? = null

    private var isMyInfoWindowShowing = false
    private var userCodeDialog: UserCodeDialog? = null
    private val addressCacheLock = Any()
    private data class CacheEntry(val address: String, val timestamp: Long)

    // 请求合并队列
    private val pendingGeocodeRequests = ConcurrentHashMap<String, MutableList<(String) -> Unit>>()

    private var lastLocationProcessTime = 0L
    private val LOCATION_DEBOUNCE_MS = 5000L
    private val LOCATION_UPDATE_INTERVAL = 30000L
    private val MIN_DISTANCE_METERS = 30f
    // 超过此距离，旧的有效地址作废
    private val ADDRESS_EXPIRY_DISTANCE_METERS = 50f

    private var mapTypeDialog: MapTypeDialog? = null
    private val mapTypeList: MutableList<MapTypeBean> = mutableListOf()

    private var myLocationIcon: BitmapDescriptor? = null
    private var markerBitmap: Bitmap? = null
    private lateinit var locationPermissionHelper: LocationPermissionHelper

    private val addressCache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    private val geocodeExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "逆地理编码异常", throwable)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun init() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        setupLocationRequest()
        setupLocationCallback()
        checkAndRequestPermissions()
        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        initMarkerIcon()
        initViewModel()
        initClickListeners()
    }


    private fun initViewModel() {
        viewModel.isLoading.observe(this) { showLoading ->
            handleLoadingState(showLoading)
        }
        viewModel.codeData.observe(this) {
            if (!TextUtils.isEmpty(it.code) && it.bitmap != null) {
                when (toNextType) {
                    0 -> {
                        showUserCodeDialog(it.code, it.bitmap!!)
                    }
                    1 -> {
                        val bundle = Bundle()
                        val stream = ByteArrayOutputStream()
                        it.bitmap!!.compress(
                            Bitmap.CompressFormat.PNG,
                            100,
                            stream
                        ) // 可选用JPEG，大小更小
                        val byteArray = stream.toByteArray()
                        bundle.putByteArray("bitmap_key", byteArray)
                        bundle.putString("code", it.code)
                        readyGo(ShareMyCodeActivity::class.java, bundle)
                    }
                }
            }
        }

        viewModel.canSend.observe(this) {
            if (!it) {
                showNoFriendDialog()
            } else {
                if (TextUtils.isEmpty(cachedAddress)) {
                    Toast.makeText(
                        this,
                        R.string.locating_please_wait,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showSosDialog()
                }
            }
        }
    }


    private fun showSosDialog() {
        if (sosDialog == null) {
            sosDialog = SosDialog(this)
            sosDialog!!.setmOnClickListener(object : SosDialog.onClickListener {
                override fun onNext() {
                    LogUtil.log("sos_dialog_click_confirm", emptyMap())
                    val bundle = Bundle()
                    bundle.putString("address", cachedAddress)
                    readyGo(SosActivity::class.java, bundle)
                }
            })
        }
        sosDialog!!.toShow()
    }
    fun showNoFriendDialog() {
        if (noFriendDialog == null) {
            noFriendDialog = NoFriendDialog(this)
            noFriendDialog!!.setmOnClickListener(object : NoFriendDialog.onClickListener {
                override fun onNext() {
                    toNextType = 1
                    viewModel.getUserCode()
                }
            })
        }
        noFriendDialog!!.toShow()
    }
    fun showUserCodeDialog(code: String, bitmap: Bitmap) {
        if (userCodeDialog == null) {
            userCodeDialog = UserCodeDialog(this)
            userCodeDialog!!.setmOnClickListener(object : UserCodeDialog.onClickListener {
                override fun onShare() {
                    shareText(code)
                }
                override fun onCopy() {
                    copyText(this@LiveLocationActivity, code)
                }
                override fun onAdd() {
                    readyGo(AddFriendActivity::class.java)
                }
            })
        }
        userCodeDialog!!.toShow(code, bitmap)
    }

    private fun initClickListeners() {
        binding.rlMove.setOnClickListener {
            if (!hasLocationPermission()) {
                permissionHintDialog?.toShow(getString(R.string.fineLocation_permission_hint), false)
                return@setOnClickListener
            }
            if (!isLocationEnabled()) {
                showLocationDisabledSnackbar()
                return@setOnClickListener
            }
            moveToMyLocationAndShowInfoWindow()
        }
        binding.rlType.setOnClickListener {
            LogUtil.log("click_maptype_btn", emptyMap())
            toShowMapTypeDialog()
        }
        binding.ivBack.setOnClickListener {
            finish()
        }
        binding.tvSos.setOnClickListener {
            LogUtil.log("click_sos_btn", emptyMap())
            toSendSos()
        }
        binding.rlTrain.setOnClickListener {
            showInterstitialAdWithStats(AreaKey.enterMetroMapAdv){
                readyGo(ShowTrainActivity::class.java)
            }

        }
        binding.rlPostion.setOnClickListener {
            LogUtil.log("click_nearbyplace_btn", emptyMap())
            readyGo(NearbyPlacesActivity::class.java)
        }

        binding.icAdd.setOnClickListener {
            toShowAddDialog()
        }
        binding.icFriend.setOnClickListener {
            LogUtil.log("click_live_locations", emptyMap())
            showInterstitialAdWithStats(AreaKey.enterFriendListAdv) {
                readyGo(LocationsListActivity::class.java)
            }
        }
        binding.icAlert.setOnClickListener {

            LogUtil.log("click_zone_alert", emptyMap())
            showInterstitialAdWithStats(AreaKey.enterZoneAlertAdv) {
                readyGo(ZoneAlertActivity::class.java)
            }
        }
        binding.icSetZone.setOnClickListener {
            LogUtil.log("click_setup_zone_btn", emptyMap())
            showInterstitialAdWithStats(AreaKey.enterZoneListAdv) {
                readyGo(ZoneListActivity::class.java)
            }
        }
        binding.ivTrack.setOnClickListener {
            showInterstitialAdWithStats(AreaKey.enterHistoryAdv){

                readyGo(FootprintsActivity::class.java)
            }

        }
    }

    fun toSendSos() {
        if (!hasLocationPermission()) {
            permissionHintDialog?.toShow(getString(R.string.fineLocation_permission_hint), false)
        } else {
            viewModel.canSendSos()
        }
    }

    private var toNextType=0

    private fun toShowAddDialog() {
        LogUtil.log("click_add_new_btn", emptyMap())
        if (addFriendDialog == null) {
            addFriendDialog = AddFriendDialog(this)
            addFriendDialog?.setmOnClickListener(object : AddFriendDialog.onClickListener {
                override fun onShare() {
                    LogUtil.log("enter_share_mycode", emptyMap())
                    toNextType = 1
                    viewModel.getUserCode()
                }
                override fun onAdd() {
                    LogUtil.log("connect_with_friend_page", emptyMap())
                    toNextType = 0
                    viewModel.getUserCode()
                }
            })
        }
        addFriendDialog?.toShow()
    }

    private fun toShowMapTypeDialog() {
        if (mapTypeDialog == null) {
            mapTypeList.clear()
            mapTypeDialog = MapTypeDialog(this)
            mapTypeList.add(
                MapTypeBean(
                    getString(R.string.map_type_general),
                    R.mipmap.ic_general,
                    GoogleMap.MAP_TYPE_NORMAL,
                    false
                )
            )
            mapTypeList.add(
                MapTypeBean(
                    getString(R.string.map_type_hybrid),
                    R.mipmap.ic_hybrid,
                    GoogleMap.MAP_TYPE_HYBRID,
                    false
                )
            )
            mapTypeList.add(
                MapTypeBean(
                    getString(R.string.map_type_satellite),
                    R.mipmap.ic_satellite,
                    GoogleMap.MAP_TYPE_SATELLITE,
                    false
                )
            )
            mapTypeList.add(
                MapTypeBean(
                    getString(R.string.map_type_terrain),
                    R.mipmap.ic_terrair,
                    GoogleMap.MAP_TYPE_TERRAIN,
                    false
                )
            )
        }
        mapTypeDialog?.setmOnClickListener(object : MapTypeDialog.onClickListener {
            override fun onCheck(type: Int) {
                mMap.mapType = type
            }
        })
        val currentType = mMap.mapType
        mapTypeList.forEach { it.isCheck = currentType == it.type }
        mapTypeDialog?.toShow(mapTypeList)
    }

    private var isRequestingLocation = false

    @SuppressLint("MissingPermission")
    private fun moveToMyLocationAndShowInfoWindow() {
        if (!isMapReady || !hasLocationPermission() || !isLocationEnabled()) return
        if (isRequestingLocation) return
        isCameraMoved = true

        val currentLast = lastLocation
        if (currentLast == null) {
            showSnackbar(getString(R.string.locating_please_wait))
        } else {
            currentLast?.let { loc ->
                moveCameraToLocation(loc)
                updateMarkerPosition(loc, true)
            }
        }

        isRequestingLocation = true
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    isRequestingLocation = false
                    if (location == null) {
                        if (currentLast == null) showSnackbar(getString(R.string.failed_location))
                        return@addOnSuccessListener
                    }
                    if (location.latitude == 0.0 && location.longitude == 0.0) {
                        if (currentLast == null) showSnackbar(getString(R.string.failed_location))
                        return@addOnSuccessListener
                    }

                    val needUpdate = currentLast == null || location.distanceTo(currentLast) > 5f
                    lastLocation = location
                    if (needUpdate) {
                        moveCameraToLocation(location)
                        updateMarkerPosition(location, true)
                    } else {
                        updateMarkerPosition(location, true)
                    }
                }
                .addOnFailureListener {
                    isRequestingLocation = false
                    if (currentLast == null) showSnackbar(getString(R.string.failed_location))
                }
        } catch (e: SecurityException) {
            isRequestingLocation = false
            Log.e(TAG, "获取当前位置权限异常", e)
            showSnackbar(getString(R.string.location_permission_required))
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun initMarkerIcon() {
        if (myLocationIcon == null && markerBitmap == null) {
            val originalBitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_map_loc)
            val targetWidth = 48.dpToPx()
            val targetHeight = 48.dpToPx()
            val scaled = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, false)
            originalBitmap.recycle() // 立即回收原图
            markerBitmap = scaled
            myLocationIcon = BitmapDescriptorFactory.fromBitmap(scaled)
        }
    }

    private var permissionHintDialog: PermissionHintDialog? = null

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun checkAndRequestPermissions() {
        locationPermissionHelper = LocationPermissionHelper(this)
        permissionHintDialog = PermissionHintDialog(this)
        initPermissionCallback()
        initPermissionDialogListener()
        startPermissionCheckFlow()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startPermissionCheckFlow() {
        if (!locationPermissionHelper.hasFineLocationPermission()) {
            permissionHintDialog?.toShow(getString(R.string.fineLocation_permission_hint), false)
        }
    }

    private fun initPermissionDialogListener() {
        permissionHintDialog?.setmOnClickListener(object : PermissionHintDialog.onClickListener {
            override fun onNext(isGetBackgroundPermission: Boolean) {
                locationPermissionHelper.checkAndRequestForeground()
            }
        })
    }

    private fun initPermissionCallback() {
        locationPermissionHelper.setCallback(object : LocationPermissionHelper.PermissionCallback {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
            override fun onFineLocationGranted() {
                LogUtil.log("agree_location1_permission", emptyMap())
                if (isMapReady) {
                    startLocationUpdates()
                    getLastKnownLocation()
                }
                startLocationReporter()
            }
            override fun onFineLocationDenied(permanentlyDenied: Boolean) {
                LogUtil.log("disagree_location1_permission", emptyMap())
                Toast.makeText(this@LiveLocationActivity, getString(R.string.fineLocation_permission_hint), Toast.LENGTH_SHORT).show()
                if (permanentlyDenied) {
                    locationPermissionHelper.navigateToAppSettings()
                }
            }
        })
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationReporter() {
        LocationReporterManager.setHighFrequency(true)   // 前台高频
        LocationReporterManager.start(applicationContext)
    }

    private fun setupLocationRequest() {
        locationRequest = if (Build.VERSION.SDK_INT >= 31) {
            LocationRequest.Builder(LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(15_000L)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()
        } else {
            @Suppress("DEPRECATION")
            LocationRequest.create().apply {
                interval = LOCATION_UPDATE_INTERVAL
                fastestInterval = 15_000L
                smallestDisplacement = MIN_DISTANCE_METERS
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                if (location.latitude == 0.0 && location.longitude == 0.0) return

                val now = System.currentTimeMillis()
                if (now - lastLocationProcessTime < LOCATION_DEBOUNCE_MS) return
                lastLocationProcessTime = now

                val isFirst = lastLocation == null
                val distance = if (isFirst) Float.MAX_VALUE else location.distanceTo(lastLocation!!)
                lastLocation = location
                if (isFirst || distance >= MIN_DISTANCE_METERS) {
                    updateMarkerPosition(location, false)
                }
                if (!isCameraMoved && isMapReady && hasLocationPermission()) {
                    moveCameraToLocation(location)
                    isCameraMoved = true
                    updateMarkerPosition(location, true)
                }
            }
        }
    }

    private fun moveCameraToLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
    }

    // ---- 地址清洗与验证 ----
    private fun cleanAddress(raw: String): String {
        // 忽略大小写，去除开头的 Plus Code（如 FCG8+CGR, ）
        return raw.replaceFirst(Regex("^[A-Z0-9]{4}\\+[A-Z0-9]{2,3},?\\s*", RegexOption.IGNORE_CASE), "").trim()
    }

    private fun isObviouslyInvalid(address: String): Boolean {
        val lower = address.lowercase(Locale.ENGLISH)
        val invalidKeywords = listOf("unnamed road", "plus code", "unmapped")
        val purePlusCode = Regex("^[A-Z0-9]{4}\\+[A-Z0-9]{2,3}$", RegexOption.IGNORE_CASE)
        return purePlusCode.matches(address) || invalidKeywords.any { lower.contains(it) }
    }

    /**
     * 更新 Marker 位置，并保证地址与当前位置绑定
     */
    private fun updateMarkerPosition(location: Location, shouldShowInfoWindow: Boolean = false) {
        if (!isMapReady || !hasLocationPermission()) return
        val latLng = LatLng(location.latitude, location.longitude)

        if (myMarker == null) {
            myMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.my_location))
                    .snippet(getString(R.string.get_address))
                    .icon(myLocationIcon)
            )
        } else {
            if (myMarker?.isInfoWindowShown == true) {
                myMarker?.hideInfoWindow()
                isMyInfoWindowShowing = false
            }
            myMarker?.position = latLng
        }

        // 检查位置是否移动过大，若已远离旧有效地址则作废
        val expired = lastValidAddressLocation?.let {
            location.distanceTo(it) > ADDRESS_EXPIRY_DISTANCE_METERS
        } ?: true

        if (expired) {
            lastValidAddress = null
            cachedAddress = null
            lastValidAddressLocation = null
            myMarker?.snippet = getString(R.string.locating_please_wait)
        }

        // 发起逆地理请求
        getAddressWithCache(location) { rawAddress ->
            if (isFinishing || isDestroyed) return@getAddressWithCache
            val cleaned = cleanAddress(rawAddress)
            if (cleaned.isNotBlank() && !isObviouslyInvalid(cleaned)) {
                myMarker?.snippet = cleaned
                cachedAddress = cleaned
                lastValidAddress = cleaned
                lastValidAddressLocation = Location(location)
                viewModel.reportAddress(cleaned)
            } else {
                Log.w(TAG, "Address became invalid after cleaning, raw: $rawAddress")
                if (lastValidAddress == null) {
                    myMarker?.snippet = getString(R.string.locating_please_wait)
                }
            }

            if (isMyInfoWindowShowing) {
                myMarker?.hideInfoWindow()
                myMarker?.showInfoWindow()
            }
        }

        if (shouldShowInfoWindow) {
            myMarker?.showInfoWindow()
            isMyInfoWindowShowing = true
        } else {
            if (myMarker?.isInfoWindowShown == false) {
                isMyInfoWindowShowing = false
            }
        }
    }

    /**
     * 逆地理编码（缓存 + 请求合并 + 清洗缓存）
     */
    private fun getAddressWithCache(location: Location, onResult: (String) -> Unit) {
        val key = "${location.latitude},${location.longitude}"
        val now = System.currentTimeMillis()

        synchronized(addressCacheLock) {
            addressCache[key]?.takeIf { now - it.timestamp < CACHE_EXPIRY_MS }?.let {
                onResult(it.address)
                return
            } ?: addressCache.remove(key)
        }

        if (!isNetworkAvailable(this)) {
            onResult(getString(R.string.network_unavailable))
            return
        }

        val callbacks = pendingGeocodeRequests.computeIfAbsent(key) { mutableListOf() }
        callbacks.add(onResult)
        if (callbacks.size > 1) return // 已有请求进行中

        lifecycleScope.launch(geocodeExceptionHandler) {
            try {
                val rawAddress = withContext(Dispatchers.IO) {
                    fetchAddressWithRetry(location)
                }

                val cleaned = rawAddress?.let { cleanAddress(it) }
                if (!cleaned.isNullOrBlank() && !isObviouslyInvalid(cleaned)) {
                    synchronized(addressCacheLock) {
                        addressCache[key] = CacheEntry(cleaned, System.currentTimeMillis())
                    }
                }

                pendingGeocodeRequests.remove(key)?.forEach { cb ->
                    if (!isFinishing && !isDestroyed) {
                        cb(rawAddress ?: getString(R.string.failed_location))
                    }
                }
            } catch (e: CancellationException) {
                // 协程正常取消，不做额外处理，但需移除回调防止泄漏
                pendingGeocodeRequests.remove(key)?.forEach { cb ->
                    if (!isFinishing && !isDestroyed) {
                        cb(getString(R.string.failed_location))
                    }
                }
                throw e
            } catch (e: Exception) {
                pendingGeocodeRequests.remove(key)?.forEach { cb ->
                    if (!isFinishing && !isDestroyed) {
                        cb(getString(R.string.failed_location))
                    }
                }
            }
        }
    }

    private suspend fun fetchAddressWithRetry(location: Location): String? {
        for (retry in 0..GEOCODE_RETRY_COUNT) {
            try {
                if (!isNetworkAvailable(this@LiveLocationActivity)) return null
                val geocoder = Geocoder(this@LiveLocationActivity, Locale.getDefault())
                val lat = location.latitude
                val lng = location.longitude

                val rawAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val addresses = suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { }
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                continuation.resume(addresses)
                            }
                            override fun onError(errorMessage: String?) {
                                continuation.resume(mutableListOf())
                            }
                        })
                    }
                    addresses.firstOrNull()?.getAddressLine(0)
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)
                }

                val cleaned = rawAddress?.let { cleanAddress(it) }
                if (!cleaned.isNullOrBlank() && !isObviouslyInvalid(cleaned)) {
                    return rawAddress // 返回原始值，由上层统一清洗
                } else {
                    Log.w(TAG, "Invalid address in retry $retry: $rawAddress")
                    if (retry < GEOCODE_RETRY_COUNT) {
                        delay(1000L * (retry + 1))
                        continue
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (retry == GEOCODE_RETRY_COUNT) return null
                delay(1000L * (retry + 1))
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!isMapReady || !hasLocationPermission() || isLocationUpdatesStarted) return
        if (!isLocationEnabled()) {
            showLocationDisabledSnackbar()
            return
        }
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            isLocationUpdatesStarted = true
        } catch (e: SecurityException) {
            Log.e(TAG, "定位权限被撤销", e)
            showSnackbar(getString(R.string.location_permission_required))
        } catch (e: Exception) {
            showSnackbar(getString(R.string.failed_location))
        }
    }

    private fun showSnackbar(message: String) {
        val rootView = findViewById<View>(R.id.content)
        rootView?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }

    private fun showLocationDisabledSnackbar() {
        val rootView = findViewById<View>(R.id.content)
        Snackbar.make(
            rootView,
            getString(R.string.location_services_are_not_enabled),
            Snackbar.LENGTH_INDEFINITE
        )
            .setAction(getString(R.string.enable)) {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }.show()
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            LocationManagerCompat.isLocationEnabled(locationManager)
        } catch (e: Exception) {
            false
        }
    }

    private fun stopLocationUpdates() {
        if (isLocationUpdatesStarted) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                Log.e(TAG, "停止定位失败", e)
            }
            isLocationUpdatesStarted = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isNetworkAvailable(this)) {
            Toast.makeText(
                this,
                getString(R.string.check_your_network),
                Toast.LENGTH_SHORT
            ).show()
        }
        if (isMapReady && hasLocationPermission()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        mapTypeDialog?.release()
        myMarker?.hideInfoWindow()
        myMarker?.remove()
        myMarker = null
        markerBitmap?.recycle()
        markerBitmap = null
        myLocationIcon = null
        cachedAddress = null
        lastValidAddress = null
        lastValidAddressLocation = null
        if (::mMap.isInitialized) {
            mMap.clear()
        }
        synchronized(addressCacheLock) {
            addressCache.clear()
        }
        addFriendDialog?.release()
        addFriendDialog=null
        permissionHintDialog?.release()
        permissionHintDialog = null
        pendingGeocodeRequests.clear()
        noFriendDialog?.release()
        sosDialog?.release()
        sosDialog = null
        userCodeDialog?.release()
        userCodeDialog=null
        noFriendDialog = null
        mapTypeDialog = null
        isMapReady = false
        isCameraMoved = false
        lastLocation = null
        mapTypeList.clear()
        super.onDestroy()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        isMapReady = true
        mMap.uiSettings.isZoomControlsEnabled = false
        mMap.uiSettings.isMyLocationButtonEnabled = false
        setupInfoWindowAdapter()

        mMap.setOnMapClickListener {
            myMarker?.hideInfoWindow()
            isMyInfoWindowShowing = false
        }

        mMap.setOnInfoWindowLongClickListener { marker ->
            val address = marker.snippet?.takeIf { it.isNotBlank() }
                ?: marker.title?.takeIf { it.isNotBlank() }
                ?: "(${marker.position.latitude}, ${marker.position.longitude})"

            val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", address)
            clipboard.setPrimaryClip(clip)
        }

        mMap.setOnMarkerClickListener { marker ->
            if (marker == myMarker) {
                marker.showInfoWindow()
                isMyInfoWindowShowing = true
            } else {
                marker.showInfoWindow()
            }
            true
        }

        mMap.setOnInfoWindowCloseListener { marker ->
            if (marker == myMarker) {
                isMyInfoWindowShowing = false
            }
        }

        if (hasLocationPermission()) {
            startLocationUpdates()
            getLastKnownLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (!isCameraMoved && location != null) {
                    if (location.latitude == 0.0 && location.longitude == 0.0) return@addOnSuccessListener
                    updateMarkerPosition(location, false)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "获取最后位置权限异常", e)
        }
    }

    private fun setupInfoWindowAdapter() {
        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? {
                return try {
                    val view = LayoutInflater.from(this@LiveLocationActivity)
                        .inflate(R.layout.pop_loction, null, false)
                    if (TextUtils.isEmpty(marker.snippet)) {
                        view.findViewById<TextView>(R.id.tv_loc).text =
                            getString(R.string.no_address)
                    } else {
                        view.findViewById<TextView>(R.id.tv_loc).text =
                            getString(R.string.currently_here) + " " + marker.snippet
                    }
                    view
                } catch (e: Exception) {
                    null
                }
            }

            override fun getInfoContents(marker: Marker): View? = null
        })
    }
}