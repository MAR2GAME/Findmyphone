package com.findmyphone.gpstracker.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson

import java.io.IOException
import java.util.concurrent.TimeUnit
import android.util.Log  // 如果项目是 Android 环境；若纯 Kotlin/JVM 可替换为 java.util.logging.Logger
import com.findmyphone.gpstracker.bean.IpInfo
import kotlin.io.use
import kotlin.jvm.java
import kotlin.text.isNullOrEmpty

object IpGeoClient {
    private const val TAG = "IpGeoClient"
    private const val IP_API_URL = "http://ip-api.com/json/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 挂起函数：获取 IP 地理位置信息
     * 在 IO 线程中执行，自动关闭 HTTP 响应。
     *
     * @return IpInfo 对象，若请求失败或解析异常则返回 null
     */
    suspend fun getIpInfo(): IpInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(IP_API_URL)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 非成功响应：记录状态码和错误体（若有）
                    val errorBody = response.body?.string()
                    Log.e(TAG, "HTTP 请求失败，状态码：${response.code}, 错误信息：$errorBody")
                    return@withContext null
                }

                val json = response.body?.string()
                if (json.isNullOrEmpty()) {
                    Log.e(TAG, "响应体为空")
                    return@withContext null
                }

                return@withContext try {
                    gson.fromJson(json, IpInfo::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "JSON 解析失败: ${e.message}")
                    null
                }
            }
        } catch (e: IOException) {
            // 网络异常、连接超时等
            Log.e(TAG, "网络请求异常: ${e.message}", e)
            null
        } catch (e: Exception) {
            // 其他未预期异常（如 OkHttp 内部状态异常）
            Log.e(TAG, "未预期的异常: ${e.message}", e)
            null
        }
    }
}