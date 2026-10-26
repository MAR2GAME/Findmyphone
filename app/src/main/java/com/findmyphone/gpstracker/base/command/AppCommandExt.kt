package  com.findmyphone.gpstracker.base.command


import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.os.Process
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.ContextCompat.startActivity
import androidx.core.net.toUri
import com.findmyphone.gpstracker.R

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone


/**
 * startActivity
 *
 * @param clazz
 */
fun Activity.readyGo(clazz: Class<*>?) {
    val intent = Intent(this, clazz)
    startActivity(intent)
}







@SuppressLint("SimpleDateFormat")
fun getDetailCurrentTime(time:Long): String {
    val date = Date(time)
    val strDateFormat = "yyyy-MM-dd HH:mm:ss"
    val format = SimpleDateFormat(strDateFormat)
    format.timeZone = TimeZone.getDefault()
    return format.format(date)
}

fun dp2px(context: Context, dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}

/**
 * startActivity with bundle
 *
 * @param clazz
 * @param bundle
 */
fun Activity.readyGo(clazz: Class<*>?, bundle: Bundle?) {
    val intent = Intent(this, clazz)
    if (null != bundle) {
        intent.putExtras(bundle)
    }
    startActivity(intent)
}

fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    // Android 5.0 (API 21) 及以上可用
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}



/**
 * startActivity then finish
 *
 * @param clazz
 */
fun Activity.readyGoThenKill(clazz: Class<*>?) {
    val intent = Intent(this, clazz)
    startActivity(intent)
    finish()
}

/**
 * startActivity with bundle then finish
 *
 * @param clazz
 * @param bundle
 */
fun Activity.readyGoThenKill(clazz: Class<*>?, bundle: Bundle?) {
    val intent = Intent(this, clazz)
    if (null != bundle) {
        intent.putExtras(bundle)
    }
    startActivity(intent)
    finish()
}


fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarseGranted = checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return fineGranted || coarseGranted
}


fun Activity.shareText(textToShare: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, textToShare)
    }
    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_to)))
}

fun copyText(
    context: Context,
    text: String,
    label: String = "Copied Text",
    isSensitive: Boolean = false,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clipData = createClipData(label, text, isSensitive)
    clipboard.setPrimaryClip(clipData)

}
private fun createClipData(label: String, text: String, isSensitive: Boolean): ClipData {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // 1. 定义数据的 MIME 类型，此处为纯文本
        val mimeTypes = arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)
        // 2. 创建 ClipDescription 对象
        val clipDescription = ClipDescription(label, mimeTypes)
        // 3. 如果需要，通过 setExtras 设置敏感标记
        if (isSensitive) {
            val extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clipDescription.setExtras(extras) // 这里是正确的调用方式
        }
        // 4. 用 ClipDescription 和文本内容创建 ClipData
        ClipData(clipDescription, ClipData.Item(text))
    } else {
        // 对于 API 33 以下的版本，保持原有的简单实现
        ClipData.newPlainText(label, text)
    }
}


 fun Context.hasLocationPermission(requireBackground: Boolean = false): Boolean {
    val hasForeground = checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    if (!hasForeground) return false
    if (requireBackground) {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    return true
}



 fun hasForegroundPermission(context: Context): Boolean {
    val fine = checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}





 fun Activity.openAppInfoSettings(pkgName: String?) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = ("package:$pkgName").toUri()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: java.lang.Exception) {
        // 最终回退方案
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
        }
    }
}





const val REQUEST_CODE_NOTIFICATION = 1003




fun getVersionName(context: Context): String {
    var manager = context.applicationContext.packageManager
    var code = ""
    try {
        var info = manager.getPackageInfo(context.applicationContext.packageName, 0)
        code = info.versionName!!
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return code
}


// 扩展函数实现防重复点击
fun View.safeClick(interval: Long = 1000, action: () -> Unit) {
    var lastClickTime = 0L
    setOnClickListener {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > interval) {
            lastClickTime = currentTime
            action()
        }
    }
}


fun Activity.rateUS(){
    try {
        val uri = ("https://play.google.com/store/apps/details?id=" +
                "" + packageName).toUri()
        val intent = Intent(Intent.ACTION_VIEW
            , uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        // 处理异常，例如显示错误消息
    }
}

fun checkNotificationPermission(context: Context): Boolean {
    val notificationManager = NotificationManagerCompat.from(context)
    val areNotificationsEnabled = notificationManager.areNotificationsEnabled()
    // 对于Android 13+，还需要检查是否明确授予了权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasNotificationPermission =
            checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        return areNotificationsEnabled && hasNotificationPermission
    }
    return areNotificationsEnabled
}


































