package com.findmyphone.gpstracker.request

import android.os.Build
import android.text.TextUtils
import android.util.Log
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.MyApplication
import com.findmyphone.gpstracker.bean.ApiResponse
import com.findmyphone.gpstracker.bean.RegisterRequest
import com.findmyphone.gpstracker.bean.TokenInfo
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.DeviceUniqueIdUtils
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ---------- 自定义异常 ----------
class HttpException(
    val code: Int,
    message: String,
    val responseBody: String? = null
) : IOException(message)

/** 用于标记 Token 过期（即使响应体为空也能识别） */
private class TokenExpiredException : IOException("Token expired")

// ---------- HttpClient ----------
object HttpClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    val gson by lazy { GsonBuilder().create() }

    private val refreshMutex = Mutex()

    private const val TOKEN_EXPIRED_CODE = 40101
    private const val TOKEN_UNAUTHORIZED_CODE = 40100

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // ==================== 注册 ====================
    suspend fun registerDevice(deviceCode: String, nickname: String): TokenInfo {
        val req = RegisterRequest(deviceCode, nickname)
        val json = gson.toJson(req)
        val rawJson = rawPostJson(AppConfig.BASE_URL + "/api/v1/auth/register", json)
        val type = object : TypeToken<ApiResponse<TokenInfo>>() {}.type
        val resp = gson.fromJson<ApiResponse<TokenInfo>>(rawJson, type)


        if (resp.code == 0 && resp.data != null) {
            // 将同步磁盘写入切换到 IO 线程
            withContext(Dispatchers.IO) {
                AppPrefsUtils.commitString(AppConfig.USER_CODE,"")
                resp.data.token?.let { AppPrefsUtils.commitString(AppConfig.TOKEN, it) }
            }
            return resp.data
        } else {
            throw HttpException(resp.code, resp.message, rawJson)
        }
    }

    // ==================== 安全刷新 Token ====================
    private suspend fun refreshTokenSync(oldToken: String): Boolean = refreshMutex.withLock {
        val currentToken = AppPrefsUtils.getString(AppConfig.TOKEN, "")
        if (currentToken.isNotEmpty() && currentToken != oldToken) {
            return@withLock true
        }
        var deviceCode = AppPrefsUtils.getString(AppConfig.DEVICE_CODE, "")
        val nickname = if (TextUtils.isEmpty(AppPrefsUtils.getString(AppConfig.NICK_NAME))) {
            "${Build.BRAND} ${Build.MODEL}"
        } else {
            AppPrefsUtils.getString(AppConfig.NICK_NAME)
        }
        if (TextUtils.isEmpty(deviceCode)) {
            deviceCode=DeviceUniqueIdUtils.getDeviceUniqueId()
        }
        val req = RegisterRequest(deviceCode, nickname)
        val json = gson.toJson(req)
        try {
            val rawJson = rawPostJson(AppConfig.BASE_URL + "/api/v1/auth/register", json)
            val type = object : TypeToken<ApiResponse<TokenInfo>>() {}.type
            val resp = gson.fromJson<ApiResponse<TokenInfo>>(rawJson, type)
            val success = resp.code == 0 && resp.data?.token != null
            if (success) {
                // 将同步磁盘写入切换到 IO 线程
                withContext(Dispatchers.IO) {
                    resp.data?.token?.let { AppPrefsUtils.commitString(AppConfig.TOKEN, it) }
                }
            }
            success
        } catch (e: Exception) {
            false
        } finally {
            AppPrefsUtils.commitString(AppConfig.USER_CODE,"")
        }
    }

    // ==================== 原始请求 ====================
    private suspend fun rawPostJson(url: String, jsonBody: String): String {
        return suspendCancellableCoroutine { cont ->
            val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(url).post(body).build()
            val call = okHttpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    val bodyStr = response.use { it.body?.string() }
                    if (response.isSuccessful && bodyStr != null) {
                        cont.resume(bodyStr)
                    } else {
                        val code = response.code
                        cont.resumeWithException(
                            HttpException(code, "HTTP $code", bodyStr)
                        )
                    }
                }
            })
        }
    }

    // ==================== 公开 HTTP 方法 ====================
    suspend fun get(url: String): String = executeRequestAutoRetry {
        Request.Builder().url(url)
    }

    suspend fun getWithParams(baseUrl: String, params: Map<String, String>): String {
        val httpUrl = baseUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid URL: $baseUrl")
        val urlBuilder = httpUrl.newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        return get(urlBuilder.build().toString())
    }

    suspend fun postJson(url: String, jsonBody: String): String = executeRequestAutoRetry {
        val body = jsonBody.toRequestBody(JSON_MEDIA_TYPE)
        Request.Builder().url(url).post(body)
    }

    suspend fun post(url: String, body: RequestBody): String = executeRequestAutoRetry {
        Request.Builder().url(url).post(body)
    }

    suspend fun delete(url: String): String = executeRequestAutoRetry {
        Request.Builder().url(url).delete()
    }

    suspend fun deleteWithParams(baseUrl: String, params: Map<String, String>): String {
        var finalUrl = baseUrl
        params.forEach { (key, value) ->
            finalUrl = finalUrl.replace("{$key}", value)
        }
        return delete(finalUrl)
    }

    // ==================== 泛型封装 ====================
    suspend inline fun <reified T> getForResponse(
        url: String,
        params: Map<String, String>? = null
    ): ApiResponse<T> {
        val json = if (params != null) getWithParams(url, params) else get(url)
        val type = object : TypeToken<ApiResponse<T>>() {}.type
        return gson.fromJson(json, type)
    }

    suspend inline fun <reified T> getForData(
        url: String,
        params: Map<String, Any>? = null
    ): T? {
        val stringParams = params?.mapValues { it.value.toString() }
        return getForResponse<T>(url, stringParams).let { resp ->
            if (resp.code == 0) resp.data
            else throw HttpException(resp.code, resp.message)
        }
    }

    suspend inline fun <reified T> postJsonForResponse(url: String, jsonBody: String): ApiResponse<T> {
        val json = postJson(url, jsonBody)
        val type = object : TypeToken<ApiResponse<T>>() {}.type
        return gson.fromJson(json, type)
    }

    suspend inline fun <reified T> postJsonForData(url: String, jsonBody: String): T? {
        return postJsonForResponse<T>(url, jsonBody).let { resp ->
            if (resp.code == 0) resp.data
            else throw HttpException(resp.code, resp.message)
        }
    }

    suspend inline fun <reified T> deleteForResponse(
        url: String,
        params: Map<String, String>? = null
    ): ApiResponse<T> {
        val json = if (params != null) deleteWithParams(url, params) else delete(url)
        val type = object : TypeToken<ApiResponse<T>>() {}.type
        return gson.fromJson(json, type)
    }

    suspend inline fun <reified T> deleteForData(
        url: String,
        params: Map<String, Any>? = null
    ): T? {
        val stringParams = params?.mapValues { it.value.toString() }
        return deleteForResponse<T>(url, stringParams).let { resp ->
            if (resp.code == 0) resp.data
            else throw HttpException(resp.code, resp.message)
        }
    }

    // ==================== 请求自动重试核心 ====================
    private suspend fun executeRequestAutoRetry(
        requestBuilder: () -> Request.Builder
    ): String {
        val oldToken = AppPrefsUtils.getString(AppConfig.TOKEN, "")
        val firstResponse: String? = try {
            executeRequestInternal(requestBuilder)
        } catch (e: TokenExpiredException) {
            null
        } catch (e: HttpException) {
            throw e
        }

        if (firstResponse != null && !isTokenExpired(firstResponse)) {
            return firstResponse
        }

        val refreshed = refreshTokenSync(oldToken)
        if (!refreshed) {
            throw IOException(MyApplication.instance.getString(R.string.request_error))
        }

        return executeRequestInternal(requestBuilder)
    }

    // ==================== 单次内部请求 ====================
    private suspend fun executeRequestInternal(
        requestBuilder: () -> Request.Builder
    ): String = suspendCancellableCoroutine { cont ->
        val builder = requestBuilder()
        val token = AppPrefsUtils.getString(AppConfig.TOKEN, "")
        val url = builder.build().url.toString()

        if (!url.contains("/auth/register") && !TextUtils.isEmpty(token)) {
            builder.addHeader("Authorization", "Bearer $token")
        }

        val request = builder.build()
        val call = okHttpClient.newCall(request)
        cont.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val body = response.use { it.body?.string() }
                        if (body != null) {
                            cont.resume(body)
                        } else {
                            cont.resumeWithException(IOException("Empty response body"))
                        }
                    } else {
                        val body = response.use { it.body?.string() }
                        if (body != null) {
                            if (isTokenExpired(body)) {
                                cont.resumeWithException(TokenExpiredException())
                            } else {
                                cont.resumeWithException(
                                    HttpException(response.code, "HTTP ${response.code}", body)
                                )
                            }
                        } else {
                            if (response.code == 401) {
                                cont.resumeWithException(TokenExpiredException())
                            } else {
                                cont.resumeWithException(IOException("Empty response body"))
                            }
                        }
                    }
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }
        })
    }

    // ==================== 过期判断 ====================
    private fun isTokenExpired(bodyString: String?): Boolean {
        if (bodyString.isNullOrBlank()) return false
        return try {
            val type = object : TypeToken<ApiResponse<*>>() {}.type
            val resp = gson.fromJson<ApiResponse<*>>(bodyString, type)
            resp.code == TOKEN_EXPIRED_CODE || resp.code == TOKEN_UNAUTHORIZED_CODE
        } catch (e: Exception) {
            false
        }
    }
}