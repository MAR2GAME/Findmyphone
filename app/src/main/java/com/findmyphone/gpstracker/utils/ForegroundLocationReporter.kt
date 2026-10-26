package com.findmyphone.gpstracker.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class ForegroundLocationReporter(context: Context) {

    // ✅ 防止内存泄漏：始终持有 ApplicationContext
    private val appContext: Context = context.applicationContext

    companion object {
        private const val TAG = "ForegroundLocationReporter"

        // 上报策略（平衡精度适配）
        private const val REPORT_INTERVAL_MS = 10 * 60_000L   // 10分钟
        private const val MIN_DISTANCE_METERS = 50f          // 50米（适配平衡精度）

        // 低频率定位参数（默认，省电）
        private const val LOW_INTERVAL_MS = 5 * 60_000L       // 5分钟
        private const val LOW_FASTEST_MS = 2 * 60_000L        // 最快2分钟
        private const val LOW_DISPLACEMENT_M = 50f           // 100米

        // 高频率定位参数（前台/实时跟踪）
        private const val HIGH_INTERVAL_MS = 30_000L          // 30秒
        private const val HIGH_FASTEST_MS = 15_000L           // 15秒
        private const val HIGH_DISPLACEMENT_M = 10f           // 10米
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    private var locationCallback: LocationCallback? = null
    private val isActive = AtomicBoolean(false)

    // 单线程调度器 + Mutex 防并发
    private var scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1)
    )

    @Volatile
    private var lastReportedLocation: Location? = null
    @Volatile
    private var lastReportedTimeMs = 0L
    private val reportMutex = Mutex()

    // ✅ 动态频率控制
    @Volatile
    private var isHighFrequency = false

    fun isActive(): Boolean = isActive.get()

    /**
     * 设置是否启用高频率定位（前台/实时需求）
     * 调用后会自动重建定位请求，立即生效
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun setHighFrequency(high: Boolean) {
        if (isHighFrequency == high) return
        isHighFrequency = high
        Log.d(TAG, "切换定位模式: ${if (high) "高频" else "低频"}")
        // 如果当前正在运行，重新启动以应用新参数
        if (isActive.get()) {
            restartLocationUpdates()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun start() {
        if (!isActive.compareAndSet(false, true)) {
            Log.d(TAG, "已在活跃状态")
            return
        }
        if (!hasLocationPermission()) {
            Log.e(TAG, "缺少位置权限")
            isActive.set(false)
            return
        }
        if (scope.coroutineContext[Job]?.isCancelled == true) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        }
        startLocationUpdates()
    }

    fun stop() {
        if (!isActive.getAndSet(false)) return
        removeLocationUpdates()
        scope.cancel()
        Log.d(TAG, "已停止")
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {
        val request = createLocationRequest()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    if (isActive.get() && scope.coroutineContext[Job]?.isActive == true) {
                        scope.launch { handleNewLocation(location) }
                    }
                }
            }
        }
        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                Log.e(TAG, "请求位置更新失败", e)
                isActive.set(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动位置监听异常", e)
            isActive.set(false)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun restartLocationUpdates() {
        removeLocationUpdates()
        startLocationUpdates()
    }

    private fun removeLocationUpdates() {
        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                Log.e(TAG, "移除位置更新失败", e)
            }
        }
        locationCallback = null
    }

    private suspend fun handleNewLocation(location: Location) {
        if (!hasLocationPermission()) return
        if (!isInternetAvailable()) return
        if (!isLocationFresh(location)) return

        val now = System.currentTimeMillis()
        var shouldReport = false

        reportMutex.withLock {
            val lastLoc = lastReportedLocation
            val timeSince = now - lastReportedTimeMs
            val distanceSince = lastLoc?.distanceTo(location) ?: MIN_DISTANCE_METERS
            shouldReport = lastLoc == null ||
                    timeSince >= REPORT_INTERVAL_MS ||
                    distanceSince >= MIN_DISTANCE_METERS
            if (shouldReport) {
                lastReportedLocation = location
                lastReportedTimeMs = now
            }
        }

        if (shouldReport) {
            reportLocation(location)
        }
    }

    // ✅ 使用 SystemClock，抵御系统时间修改
    private fun isLocationFresh(location: Location, maxAgeSeconds: Long = 120): Boolean {
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        val maxAgeNanos = maxAgeSeconds * 1_000_000_000L
        return ageNanos <= maxAgeNanos
    }

    private suspend fun reportLocation(location: Location) {
        try {
            val requestBody = mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude
            )
            // 如果 AppPrefsUtils.commitDouble 是同步提交，建议改为 apply() 或移除
            AppPrefsUtils.commitDouble("latitude", location.latitude)
            AppPrefsUtils.commitDouble("longitude", location.longitude)

            val json = HttpClient.gson.toJson(requestBody)
            HttpClient.postJsonForData<Any>(
                url = AppConfig.BASE_URL + "/api/v1/locations/report",
                jsonBody = json
            )
            Log.d(TAG, "上报成功")
        } catch (e: Exception) {
            Log.e(TAG, "上报失败", e)
        }
    }

    // ✅ 真实互联网连接判断
    private fun isInternetAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network: Network? = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    // ✅ 动态生成 LocationRequest，根据 isHighFrequency 选择参数和优先级
    private fun createLocationRequest(): LocationRequest {
        return if (Build.VERSION.SDK_INT >= 31) {
            if (isHighFrequency) {
                LocationRequest.Builder(HIGH_INTERVAL_MS)
                    .setMinUpdateIntervalMillis(HIGH_FASTEST_MS)
                    .setMinUpdateDistanceMeters(HIGH_DISPLACEMENT_M)
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)   // 仅高频时使用高精度
                    .build()
            } else {
                LocationRequest.Builder(LOW_INTERVAL_MS)
                    .setMinUpdateIntervalMillis(LOW_FASTEST_MS)
                    .setMinUpdateDistanceMeters(LOW_DISPLACEMENT_M)
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY) // 默认平衡省电
                    .build()
            }
        } else {
            @Suppress("DEPRECATION")
            LocationRequest.create().apply {
                if (isHighFrequency) {
                    interval = HIGH_INTERVAL_MS
                    fastestInterval = HIGH_FASTEST_MS
                    smallestDisplacement = HIGH_DISPLACEMENT_M
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                } else {
                    interval = LOW_INTERVAL_MS
                    fastestInterval = LOW_FASTEST_MS
                    smallestDisplacement = LOW_DISPLACEMENT_M
                    priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                }
            }
        }
    }
}