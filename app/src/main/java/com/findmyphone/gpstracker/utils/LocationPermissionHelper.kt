package com.findmyphone.gpstracker.utils
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlin.apply

class LocationPermissionHelper(private val activity: AppCompatActivity) {

    private val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    private val coarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION
    private val backgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    // 前台权限请求 launcher
    private val foregroundLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fineGranted = result[fineLocationPermission] == true
        val coarseGranted = result[coarseLocationPermission] == true
        val anyLocationGranted = fineGranted || coarseGranted

        if (anyLocationGranted) {
            callback?.onFineLocationGranted()
        } else {
            val isPermanentlyDenied =
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, fineLocationPermission)
            callback?.onFineLocationDenied(isPermanentlyDenied)
        }
    }

    // 后台权限请求 launcher
    private val backgroundLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            callback?.onBackgroundLocationGranted()
        } else {
            val shouldShowRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(activity, backgroundLocationPermission)
            callback?.onBackgroundLocationDenied(
                notAlways = shouldShowRationale,
                permanentlyDenied = !shouldShowRationale
            )
        }
    }

    private var callback: PermissionCallback? = null

    fun checkAndRequestForeground() {
        foregroundLauncher.launch(arrayOf(fineLocationPermission, coarseLocationPermission))
    }

    fun requestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!hasBackgroundPermission()) {
                backgroundLauncher.launch(backgroundLocationPermission)
            } else {
                callback?.onBackgroundLocationGranted()
            }
        } else {
            callback?.onBackgroundLocationGranted()
        }
    }

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, fineLocationPermission) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(activity, coarseLocationPermission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(activity, backgroundLocationPermission) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun navigateToAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${activity.packageName}".toUri()
            activity.startActivity(this)
        }
    }

    fun setCallback(callback: PermissionCallback?) {
        this.callback = callback
    }

    interface PermissionCallback {
        fun onFineLocationGranted(){}
        fun onFineLocationDenied(permanentlyDenied: Boolean){}
        fun onBackgroundLocationGranted(){}
        fun onBackgroundLocationDenied(notAlways: Boolean, permanentlyDenied: Boolean){}
    }
}