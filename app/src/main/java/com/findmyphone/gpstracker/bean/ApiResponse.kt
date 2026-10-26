package com.findmyphone.gpstracker.bean

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null   // 可空，因为错误响应可能无 data
)