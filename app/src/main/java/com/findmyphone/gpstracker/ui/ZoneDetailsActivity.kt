package com.findmyphone.gpstracker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.MyApplication
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.bean.ZoneEvent
import com.findmyphone.gpstracker.bean.ZoneItem
import com.findmyphone.gpstracker.databinding.ActivityZoneDetailsBinding
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.view.LocationSearchPopupWindow
import com.findmyphone.gpstracker.view.RectSliderView
import com.findmyphone.gpstracker.view.SetZoneDialog
import com.findmyphone.gpstracker.view.ZoneHintDialog
import com.findmyphone.gpstracker.viewmodel.ZoneDetailsViewModel
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.greenrobot.eventbus.EventBus
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ZoneDetailsActivity : BaseMvvmActivity<ActivityZoneDetailsBinding, ZoneDetailsViewModel>(),
    OnMapReadyCallback {

    companion object {
        private const val DEFAULT_RADIUS_METERS = 40.0
        private const val MIN_RADIUS = 40.0
        private const val MAX_RADIUS = 500.0
        private const val MAP_LOAD_TIMEOUT_MS = 5000L
        private const val STATE_KEY_LAT = "center_lat"
        private const val STATE_KEY_LNG = "center_lng"
        private const val STATE_KEY_RADIUS = "current_radius"

        // [优化] 新增：最大缩放级别限制，防止标签消失（值越小越缩小，这里限制放大上限）
        private const val MAX_ZOOM_LEVEL = 18.0f
    }

    private var googleMap: GoogleMap? = null
    private var centerLatLng: LatLng? = null
    private var zoneHintDialog: ZoneHintDialog? = null
    private var currentRadiusMeters = DEFAULT_RADIUS_METERS
    private var geoFenceCircle: Circle? = null
    private var setZoneDialog: SetZoneDialog? = null
    private var centerMarker: Marker? = null
    private var isMapReady = false
    private var pendingRadiusUpdate = false
    private var mapLoadTimeoutJob: Job? = null

    private lateinit var placesClient: PlacesClient
    private lateinit var zoneItem: ZoneItem
    private var type = 0 // 0: 新建, 1: 编辑

    private var searchPopupWindow: LocationSearchPopupWindow? = null
    private var sessionToken: AutocompleteSessionToken? = null

    private var zoneName = ""

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var zoneNames: MutableList<String> = mutableListOf(
        "Add your home",
        "Add your company",
        "Add your school",
        "Add your custom zone"
    )

    override fun init() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        parseIntentData()
        initViewModel()
        if (centerLatLng == null) {
            requestCurrentLocation()
        }
        setupMapFragment()
        setupSlider()
        setupBackPressHandler()
        setupBackButton()
        setupSearch()
        checkGooglePlayServices()
        initPlacesApi()
        startMapLoadTimeout()
    }

    private fun requestCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            fallbackToDefaultLocation(getString(R.string.invalid_location_using_default))
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (!isFinishing && !isDestroyed) {
                    if (location != null) {
                        Log.e("weiyu","走的这里2")
                        centerLatLng = LatLng(location.latitude, location.longitude)
                        if (isMapReady) {
                            moveToLocation(centerLatLng!!, null)
                        }
                    } else {
                        fallbackToDefaultLocation(getString(R.string.invalid_location_using_default))
                    }
                }
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    fallbackToDefaultLocation(getString(R.string.invalid_location_using_default))
                }
            }
    }

    private fun fallbackToDefaultLocation(message: String) {
        centerLatLng = LatLng(40.7580, -73.9855) // 纽约
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (isMapReady) {
            updateCenterMarker()
            updateGeofenceCircle()
            zoomToFitCircle()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    fun onBack() {
        showInterstitialAdWithStats(AreaKey.zoneEditBackAdv) {
            finish()
        }
    }

    private fun initViewModel() {
        viewModel.isLoading.observe(this) {
            handleLoadingState(it)
        }
        viewModel.isSaveState.observe(this) {
            if (it) {
                Toast.makeText(this,R.string.saved_successfully, Toast.LENGTH_SHORT).show()
                EventBus.getDefault().post(ZoneEvent(true))
                showInterstitialAdWithStats(AreaKey.zoneSaveBackAdv) {
                    finish()
                }
            } else {
                handleLoadingState(false)
            }
        }
        viewModel.isDeleteState.observe(this) {
            if (it) {
                Toast.makeText(this,R.string.delete_successfully, Toast.LENGTH_SHORT).show()
                EventBus.getDefault().post(ZoneEvent(true))
                showInterstitialAdWithStats(AreaKey.zoneSaveBackAdv) {
                    finish()
                }
            } else {
                handleLoadingState(false)
            }
        }
    }

    private fun checkGooglePlayServices() {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(this, resultCode, 1001)?.show()
            } else {
                Toast.makeText(this, R.string.error_google_play_services, Toast.LENGTH_LONG).show()
                finish()
            }
            return
        }
        val version = googleApiAvailability.getApkVersion(this)
        if (version < 21000000) {
            Toast.makeText(this, R.string.update_google_play_services, Toast.LENGTH_LONG).show()
        }
    }

    private fun initPlacesApi() {
        if (!Places.isInitialized()) {
            Toast.makeText(this,R.string.places_api_not_initialized, Toast.LENGTH_LONG).show()
            binding.etName.isEnabled = false
            binding.tvSearch.isEnabled = false
            return
        }
        try {
            placesClient = MyApplication.placesClient
            sessionToken = AutocompleteSessionToken.newInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.error_init_places_api, Toast.LENGTH_LONG).show()
            binding.etName.isEnabled = false
            binding.tvSearch.isEnabled = false
        }
    }

    private fun parseIntentData() {
        val incoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("zoneData", ZoneItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("zoneData")
        }
        if (incoming == null) {
            finish()
            return
        }
        zoneItem = incoming
        type = if (zoneItem.centerLatitude != null) 1 else 0
        val latitude = zoneItem.centerLatitude ?: AppPrefsUtils.getDouble("latitude", Double.NaN)
        val longitude = zoneItem.centerLongitude ?: AppPrefsUtils.getDouble("longitude", Double.NaN)
        if (centerLatLng == null && !latitude.isNaN() && !longitude.isNaN()) {
            centerLatLng = LatLng(latitude, longitude)
        }
        val radius = zoneItem.radiusMeters?.toDouble() ?: DEFAULT_RADIUS_METERS
        currentRadiusMeters = radius.coerceIn(MIN_RADIUS, MAX_RADIUS)
        zoneName = if (TextUtils.isEmpty(zoneItem.zoneName)) {
            getString(R.string.custom_zone)
        } else {
            if (zoneItem.defaultTemplate == true) {
                if (zoneNames.contains(zoneItem.zoneName)) {
                    when (zoneItem.zoneName) {
                        "Add your home" -> getString(R.string.home)
                        "Add your company" -> getString(R.string.work)
                        "Add your school" -> getString(R.string.school)
                        "Add your custom zone" -> getString(R.string.custom_zone)
                        else -> getString(R.string.custom_zone)
                    }
                } else {
                    zoneItem.zoneName
                }
            } else {
                zoneItem.zoneName
            }
        }
    }

    private var mapFragment: SupportMapFragment? = null

    private fun setupMapFragment() {
        mapFragment = supportFragmentManager.findFragmentById(R.id.info_map) as SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    private fun startMapLoadTimeout() {
        mapLoadTimeoutJob = lifecycleScope.launch {
            delay(MAP_LOAD_TIMEOUT_MS)
            if (!isMapReady && !isFinishing && !isDestroyed) {
                binding.slider.isEnabled = true
                Toast.makeText(
                    this@ZoneDetailsActivity,
                    R.string.map_loading_slow,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupSlider() {
        binding.slider.apply {
            minValue = MIN_RADIUS.toFloat()
            maxValue = MAX_RADIUS.toFloat()
            currentValue = currentRadiusMeters.toFloat()
            isEnabled = false
            setOnSlideStopListener(object : RectSliderView.OnSlideStopListener {
                override fun onSlideStop(
                    view: RectSliderView,
                    finalValue: Float,
                    finalProgress: Float
                ) {
                    currentRadiusMeters = finalValue.toDouble()
                    if (isMapReady && googleMap != null && centerLatLng != null) {
                        updateGeofenceCircle()
                        zoomToFitCircle()
                    } else {
                        pendingRadiusUpdate = true
                    }
                }
            })
        }

        binding.ivHint.setOnClickListener {
            if (zoneHintDialog == null) {
                zoneHintDialog = ZoneHintDialog(this)
            }
            zoneHintDialog?.toShow()
        }

        binding.etName.addTextChangedListener(
            afterTextChanged = { text ->
                binding.ivClose.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        )

        binding.tvSearch.setOnClickListener {
            val query = binding.etName.text.toString().trim()
            if (query.isNotEmpty()) {
                performLocationSearch(query)
            } else {
                Toast.makeText(this, R.string.enter_location, Toast.LENGTH_SHORT).show()
            }
        }

        binding.ivClose.setOnClickListener {
            binding.etName.text.clear()
        }

        binding.btNext.setOnClickListener {
            if (setZoneDialog == null) {
                setZoneDialog = SetZoneDialog(this)
                setZoneDialog!!.setmOnClickListener(object : SetZoneDialog.onClickListener {
                    override fun onSave(name: String, enterCheck: Boolean, leaveCheck: Boolean) {
                        LogUtil.log("setup_zone_click_save", emptyMap())
                        zoneItem.apply {
                            zoneName = name
                            onEnter = enterCheck
                            onLeave = leaveCheck
                            radiusMeters = currentRadiusMeters.toInt()
                            centerLatitude = centerLatLng?.latitude
                            centerLongitude = centerLatLng?.longitude
                        }
                        viewModel.save(zoneItem)
                    }

                    override fun onDelete() {
                        LogUtil.log("setup_zone_click_delete", emptyMap())
                        zoneItem.zoneId?.let {
                            viewModel.delete(it)
                        } ?: Toast.makeText(
                            this@ZoneDetailsActivity,
                            R.string.cannot_delete_new_zone,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            }
            if (setZoneDialog?.isShowing != true) {
                setZoneDialog?.toShow(zoneName, type, zoneItem.onEnter, zoneItem.onLeave)
            }
        }
    }

    private fun setupBackButton() {
        binding.ivBack.setOnClickListener {
            onBack()
        }
    }

    private fun setupSearch() {
        binding.etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etName.text.toString().trim()
                if (query.isNotEmpty()) {
                    performLocationSearch(query)
                } else {
                    Toast.makeText(this, R.string.enter_location, Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }
    }

    private fun performLocationSearch(query: String) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etName.windowToken, 0)
        binding.etName.clearFocus()

        if (!::placesClient.isInitialized) {
            Toast.makeText(this, R.string.places_api_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        val currentSessionToken = AutocompleteSessionToken.newInstance()
        sessionToken = currentSessionToken

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(currentSessionToken)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                if (!isFinishing && !isDestroyed) {
                    val predictions = response.autocompletePredictions
                    if (predictions.isNotEmpty()) {
                        showSearchResults(predictions, currentSessionToken)
                    } else {
                        Toast.makeText(this, R.string.no_search_results, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { exception ->
                if (!isFinishing && !isDestroyed) {
                    handlePlacesError(exception)
                }
            }
    }

    private fun handlePlacesError(exception: Exception) {
        Log.e("ZoneDetails", "Places API error", exception)
        val message = when (exception) {
            is IOException -> getString(R.string.error_network)
            is ApiException -> {
                when (exception.statusCode) {
                    1000 -> getString(R.string.error_quota_exceeded)
                    1001 -> getString(R.string.error_permission_denied)
                    1003 -> getString(R.string.error_api_key_invalid)
                    else -> "${getString(R.string.search_failed)}: ${exception.statusMessage}"
                }
            }
            else -> "${getString(R.string.search_failed)}: ${exception.message}"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSearchResults(
        predictions: List<AutocompletePrediction>,
        token: AutocompleteSessionToken
    ) {
        searchPopupWindow?.dismiss()
        val anchorView = binding.etName
        searchPopupWindow = LocationSearchPopupWindow(this, predictions) { prediction ->
            fetchPlaceDetailsAndMove(prediction, token)
        }
        searchPopupWindow?.show(anchorView)
    }

    private fun fetchPlaceDetailsAndMove(
        prediction: AutocompletePrediction,
        token: AutocompleteSessionToken?
    ) {
        if (!::placesClient.isInitialized) {
            Toast.makeText(this, R.string.places_api_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val place = suspendFetchPlace(placesClient, prediction.placeId, token)
                if (!isFinishing && !isDestroyed && place != null) {
                    val displayName = place.getDisplayName()
                    val location = place.getLocation()
                    if (displayName != null && location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        moveToLocation(latLng, displayName.toString())
                    } else {
                        val fallbackName = prediction.getPrimaryText(null).toString()
                        if (location != null) {
                            val latLng = LatLng(location.latitude, location.longitude)
                            moveToLocation(latLng, fallbackName)
                        } else {
                            Toast.makeText(
                                this@ZoneDetailsActivity,
                                R.string.location_not_found,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isFinishing && !isDestroyed) {
                    handlePlacesError(e)
                }
            }
        }
    }

    private suspend fun suspendFetchPlace(
        client: PlacesClient,
        placeId: String,
        sessionToken: AutocompleteSessionToken?
    ): Place? = suspendCancellableCoroutine { continuation ->
        val requestBuilder = FetchPlaceRequest.builder(
            placeId,
            listOf(Place.Field.DISPLAY_NAME, Place.Field.LOCATION)
        )
        sessionToken?.let { requestBuilder.setSessionToken(it) }
        val request = requestBuilder.build()

        client.fetchPlace(request)
            .addOnSuccessListener { response ->
                if (continuation.isActive) {
                    continuation.resume(response.place)
                }
            }
            .addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
    }

    private fun updateCenterMarker() {
        centerMarker?.remove()
        centerLatLng?.let { latLng ->
            centerMarker = googleMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .draggable(false)
            )
        }
    }

    private fun moveToLocation(latLng: LatLng, locationName: String?) {
        centerLatLng = latLng
        if (!locationName.isNullOrEmpty()) {
            binding.etName.setText(locationName)
        }

        if (isMapReady && googleMap != null) {
            updateCenterMarker()
        }

        updateGeofenceCircle()

        if (isMapReady && googleMap != null && centerLatLng != null && !isFinishing && !isDestroyed) {
            zoomToFitCircle()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        mapLoadTimeoutJob?.cancel()
        googleMap = map
        isMapReady = true
        binding.slider.isEnabled = true
        configureMap()
        updateCenterMarker()
        updateGeofenceCircle()
        zoomToFitCircle()

        if (pendingRadiusUpdate) {
            updateGeofenceCircle()
            zoomToFitCircle()
            pendingRadiusUpdate = false
        }

        map.setOnCameraIdleListener {
            updateCenterFromMap()
        }
    }

    private fun updateCenterFromMap() {
        val newCenter = googleMap?.cameraPosition?.target
        if (newCenter != null && centerLatLng != newCenter) {
            centerLatLng = newCenter
            centerMarker?.position = newCenter
            geoFenceCircle?.center = newCenter
        }
    }

    // [优化] 配置地图：增加最大缩放限制，防止地点标签消失
    private fun configureMap() {
        googleMap?.apply {
            uiSettings?.isZoomControlsEnabled = true
            uiSettings?.isMyLocationButtonEnabled = false
            mapType = GoogleMap.MAP_TYPE_NORMAL
            setMaxZoomPreference(MAX_ZOOM_LEVEL)  // [优化] 限制放大上限，保留地点标签
        }
    }

    private fun updateGeofenceCircle() {
        if (googleMap == null || centerLatLng == null) return
        geoFenceCircle?.remove()
        geoFenceCircle = googleMap?.addCircle(
            CircleOptions()
                .center(centerLatLng!!)
                .radius(currentRadiusMeters)
                .strokeColor(ContextCompat.getColor(this, R.color.geo_fence_stroke))
                .strokeWidth(4f)
                .fillColor(ContextCompat.getColor(this, R.color.geo_fence_fill))
        )
    }

    // [优化] 调整视野：增大 padding 并利用 maxZoom 限制避免标签消失
    private fun zoomToFitCircle() {
        if (isFinishing || isDestroyed) return
        if (googleMap == null || centerLatLng == null) return
        val view = mapFragment?.view
        if (view == null || view.width == 0 || view.height == 0) {
            view?.post { zoomToFitCircle() }
            return
        }
        val bounds = LatLngBounds.Builder()
            .include(getLatLngAtDistance(centerLatLng!!, currentRadiusMeters, 0.0))
            .include(getLatLngAtDistance(centerLatLng!!, currentRadiusMeters, 90.0))
            .include(getLatLngAtDistance(centerLatLng!!, currentRadiusMeters, 180.0))
            .include(getLatLngAtDistance(centerLatLng!!, currentRadiusMeters, 270.0))
            .build()

        // [优化] 增大 padding 到 100dp，让相机后退，视野比圆更大，降低实际缩放级别
        val paddingPx = (100 * resources.displayMetrics.density).toInt()
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
    }

    private fun getLatLngAtDistance(
        center: LatLng,
        radiusMeters: Double,
        bearingDeg: Double
    ): LatLng {
        val lat1 = Math.toRadians(center.latitude)
        val lon1 = Math.toRadians(center.longitude)
        val bearing = Math.toRadians(bearingDeg)
        val angularDistance = radiusMeters / 6371000.0

        val lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(angularDistance) +
                    Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearing)
        )
        val lon2 = lon1 + Math.atan2(
            Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(lat1),
            Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2)
        )

        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    override fun onDestroy() {
        mapLoadTimeoutJob?.cancel()
        googleMap?.setOnCameraIdleListener(null)
        geoFenceCircle?.remove()
        centerMarker?.remove()
        googleMap = null
        isMapReady = false
        zoneHintDialog?.release()
        zoneHintDialog = null
        searchPopupWindow?.dismiss()
        searchPopupWindow = null
        setZoneDialog?.release()
        setZoneDialog = null
        super.onDestroy()
    }
}