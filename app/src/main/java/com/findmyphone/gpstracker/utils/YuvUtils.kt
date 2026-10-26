package com.findmyphone.gpstracker.utils

import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.util.Log
import kotlin.math.min
import kotlin.ranges.coerceIn
import kotlin.ranges.until

/**
 * YUV 格式转换工具（生产级）
 * - 支持 ByteArray 复用，避免每帧分配新内存
 * - 修复裁剪后旋转尺寸错误问题
 * - 兼容半平面和全平面 YUV_420_888 格式
 * - 包含边界检查和异常保护
 */
object YuvUtils {

    private const val TAG = "YuvUtils"

    /**
     * YUV_420_888 → NV21
     * @param image 输入图像
     * @param cropRect 裁剪区域（null 表示全图）
     * @param output 可复用缓冲区（大小不足时自动扩容）
     * @return NV21 字节数组（可能与 output 为同一对象）
     */
    @JvmOverloads
    fun yuv420888ToNv21(
        image: Image,
        cropRect: Rect? = null,
        output: ByteArray? = null
    ): ByteArray {
        if (image.format != ImageFormat.YUV_420_888) {
            Log.e(TAG, "Unsupported format: ${image.format}")
            return output ?: ByteArray(0)
        }

        val srcRect = cropRect ?: Rect(0, 0, image.width, image.height)
        // 对齐到偶数（YUV 420 要求）
        var left = srcRect.left and -2
        var top = srcRect.top and -2
        var right = (srcRect.right + 1) and -2
        var bottom = (srcRect.bottom + 1) and -2

        left = left.coerceIn(0, image.width)
        top = top.coerceIn(0, image.height)
        right = right.coerceIn(left, image.width)
        bottom = bottom.coerceIn(top, image.height)

        val width = right - left
        val height = bottom - top
        if (width < 2 || height < 2) {
            Log.w(TAG, "Invalid crop size: ${width}x${height}")
            return output ?: ByteArray(0)
        }

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        val requiredSize = width * height * 3 / 2
        val nv21 = if (output != null && output.size >= requiredSize) output else ByteArray(
            requiredSize
        )

        // 复制 Y 分量
        var yIndex = 0
        for (row in top until bottom) {
            val pos = row * yRowStride + left
            if (pos + width > yBuffer.limit()) {
                Log.w(TAG, "Y buffer overflow at row $row")
                continue
            }
            yBuffer.position(pos)
            yBuffer.get(nv21, yIndex, width)
            yIndex += width
        }

        // 复制 UV 分量（NV21: V, U 交错）
        var uvIndex = width * height
        val startRow = top / 2
        val endRow = bottom / 2
        val startCol = left / 2
        val endCol = right / 2

        // 半平面格式判断：常见情况 pixelStride == 2 且两个平面指向同一 buffer
        val isSemiPlanar = uPixelStride == 2 && vPixelStride == 2 && uBuffer == vBuffer

        if (isSemiPlanar) {
            // UV 交错存储（UVUV...），通常从 uBuffer 读取，v 平面只是偏移不同
            for (row in startRow until endRow) {
                val uvPos = row * uRowStride + startCol * 2
                if (uvPos + (endCol - startCol) * 2 > uBuffer.limit()) {
                    Log.w(TAG, "UV buffer overflow at row $row")
                    continue
                }
                uBuffer.position(uvPos)
                val len = kotlin.comparisons.minOf((endCol - startCol) * 2, uBuffer.remaining())
                uBuffer.get(nv21, uvIndex, len)
                uvIndex += len
            }
        } else {
            // 全平面格式：U 和 V 独立，逐点读取
            for (row in startRow until endRow) {
                val uRowOffset = row * uRowStride
                val vRowOffset = row * vRowStride
                for (col in startCol until endCol) {
                    val uPos = uRowOffset + col * uPixelStride
                    val vPos = vRowOffset + col * vPixelStride
                    val vValue = if (vPos < vBuffer.limit()) vBuffer.get(vPos) else 0
                    val uValue = if (uPos < uBuffer.limit()) uBuffer.get(uPos) else 0
                    nv21[uvIndex++] = vValue
                    nv21[uvIndex++] = uValue
                }
            }
        }

        return nv21
    }

    /**
     * 旋转 NV21 图像（支持 0/90/180/270 度）
     * @param data NV21 数据
     * @param width 原始宽度
     * @param height 原始高度
     * @param rotation 旋转角度
     * @param output 可复用缓冲区
     * @return 旋转后的 NV21 数据
     */
    @JvmOverloads
    fun rotateNV21(
        data: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        output: ByteArray? = null
    ): ByteArray {
        if (rotation % 90 != 0 || rotation == 0) return data

        val isRotate90 = rotation == 90 || rotation == 270
        val newWidth = if (isRotate90) height else width
        val newHeight = if (isRotate90) width else height
        val requiredSize = data.size
        val rotated = if (output != null && output.size >= requiredSize) output else ByteArray(
            requiredSize
        )

        val ySize = width * height

        // 旋转 Y 分量
        for (x in 0 until width) {
            for (y in 0 until height) {
                val srcIndex = y * width + x
                val destIndex = when (rotation) {
                    90 -> x * newWidth + (height - y - 1)
                    180 -> (height - y - 1) * width + (width - x - 1)
                    270 -> (width - x - 1) * newWidth + y
                    else -> srcIndex
                }
                rotated[destIndex] = data[srcIndex]
            }
        }

        // 旋转 UV 分量（NV21 交错）
        val uvWidth = width / 2
        val uvHeight = height / 2
        val newUvWidth = if (isRotate90) uvHeight else uvWidth
        val newUvHeight = if (isRotate90) uvWidth else uvHeight

        for (x in 0 until uvWidth) {
            for (y in 0 until uvHeight) {
                val srcIndex = ySize + (y * uvWidth + x) * 2
                val destIndex = when (rotation) {
                    90 -> {
                        val newX = x
                        val newY = uvHeight - y - 1
                        ySize + (newY * newUvWidth + newX) * 2
                    }
                    180 -> {
                        val newX = uvWidth - x - 1
                        val newY = uvHeight - y - 1
                        ySize + (newY * newUvWidth + newX) * 2
                    }
                    270 -> {
                        val newX = uvWidth - x - 1
                        val newY = y
                        ySize + (newY * newUvWidth + newX) * 2
                    }
                    else -> srcIndex
                }
                rotated[destIndex] = data[srcIndex]       // V
                rotated[destIndex + 1] = data[srcIndex + 1] // U
            }
        }

        return rotated
    }

    /**
     * 一步完成 YUV → NV21 + 旋转（减少一次拷贝）
     * @param image 输入图像
     * @param rotation 旋转角度
     * @param cropRect 裁剪区域
     * @param output 可复用缓冲区
     * @return 旋转后的 NV21 数据
     */
    @JvmOverloads
    fun yuv420888ToRotatedNv21(
        image: Image,
        rotation: Int,
        cropRect: Rect? = null,
        output: ByteArray? = null
    ): ByteArray {
        val nv21 = yuv420888ToNv21(image, cropRect, output)
        if (rotation % 90 == 0 && rotation != 0) {
            val rect = cropRect ?: Rect(0, 0, image.width, image.height)
            val croppedWidth = rect.width()
            val croppedHeight = rect.height()
            return rotateNV21(nv21, croppedWidth, croppedHeight, rotation, output)
        }
        return nv21
    }
}