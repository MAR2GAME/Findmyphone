package com.findmyphone.gpstracker.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.EnumMap

/**
 * 二维码生成器（协程版本，支持自定义颜色、边距、纠错等级）
 *
 * 特性：
 * - 完全线程安全（每次生成使用新的 QRCodeWriter 实例）
 * - 支持自定义尺寸、颜色、白边裁剪
 * - 协程取消响应（通过 yield）
 * - 内存友好（裁剪时直接复用像素数组）
 *
 * 使用示例：
 * ```
 * val bitmap = QrCodeGenerator.createQrCode(
 *     content = "https://example.com",
 *     size = 500,
 *     keepMargin = false,
 *     codeColor = Color.BLACK,
 *     bgColor = Color.WHITE,
 *     errorCorrectionLevel = ErrorCorrectionLevel.M
 * )
 * imageView.setImageBitmap(bitmap)
 * ```
 */
object QrCodeGenerator {

    private const val MIN_SIZE = 100
    private const val MAX_SIZE = 2000
    private const val MAX_CONTENT_LENGTH = 2000

    /**
     * 异步生成二维码
     *
     * @param content 编码内容（非空，长度 <= 2000，实际容量依赖纠错等级和编码模式）
     * @param size 生成尺寸（像素），会被限制在 [MIN_SIZE, MAX_SIZE] 范围内
     * @param keepMargin 是否保留 ZXing 默认白边（若为 false 会进一步自动裁剪多余空白）
     * @param codeColor 二维码点的颜色（ARGB，推荐不透明）
     * @param bgColor 背景色（ARGB，推荐不透明）
     * @param errorCorrectionLevel 纠错等级（L/M/Q/H）
     * @return 生成的 Bitmap，失败或内容无效时返回 null
     */
    suspend fun createQrCode(
        content: String,
        size: Int = 500,
        keepMargin: Boolean = true,
        codeColor: Int = Color.BLACK,
        bgColor: Int = Color.WHITE,
        errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.M
    ): Bitmap? = withContext(Dispatchers.IO) {
        // 内容有效性检查
        if (content.isBlank() || content.length > MAX_CONTENT_LENGTH) {
            return@withContext null
        }

        val finalSize = size.coerceIn(MIN_SIZE, MAX_SIZE)

        // 配置编码参数
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel)
            // 若不需要保留白边，设置最小边距（后续还会裁剪）
            put(EncodeHintType.MARGIN, if (keepMargin) 4 else 1)
        }

        return@withContext try {
            // 【修复2】每次局部创建新的 QRCodeWriter，避免线程安全问题
            val qrWriter = QRCodeWriter()
            val bitMatrix = qrWriter.encode(content, BarcodeFormat.QR_CODE, finalSize, finalSize, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            // 填充像素数组
            for (y in 0 until height) {
                yield() // 响应协程取消
                val rowOffset = y * width
                for (x in 0 until width) {
                    pixels[rowOffset + x] = if (bitMatrix[x, y]) codeColor else bgColor
                }
            }

            // 创建原始 Bitmap
            var bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

            // 若不需要白边，进行裁剪
            if (!keepMargin) {
                val cropped = cropToContent(pixels, width, height, bgColor)
                if (cropped != null) {
                    bitmap.recycle()     // 回收原始图
                    bitmap = cropped
                } else {
                    bitmap.recycle()
                    return@withContext null
                }
            }

            bitmap
        } catch (e: WriterException) {
            // 内容过长或编码参数不合法
            e.printStackTrace()
            Log.e("weiyu","错误"+e.toString())
            null
        } catch (e: IllegalArgumentException) {
            // 尺寸为0或其他参数非法
            e.printStackTrace()
            Log.e("weiyu","错误"+e.toString())
            null
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("weiyu","错误"+e.toString())
            null
        }
    }

    /**
     * 裁剪掉二维码周围的纯背景色区域
     *
     * @param pixels 原始像素数组（行优先）
     * @param width 原始宽度
     * @param height 原始高度
     * @param bgColor 背景色（ARGB）
     * @return 裁剪后的 Bitmap，若无有效内容或裁剪后尺寸为0则返回 null
     */
    @WorkerThread
    private suspend fun cropToContent(
        pixels: IntArray,
        width: Int,
        height: Int,
        bgColor: Int
    ): Bitmap? {
        if (width == 0 || height == 0) return null

        // 判断是否为背景色（完全匹配，包含 alpha 通道）
        fun isBg(pixel: Int) = pixel == bgColor

        var top = 0
        var bottom = height - 1
        var left = 0
        var right = width - 1
        var hasContent = false

        // 扫描上边界
        for (y in 0 until height) {
            yield()
            var allBg = true
            for (x in 0 until width) {
                if (!isBg(pixels[y * width + x])) {
                    allBg = false
                    hasContent = true
                    break
                }
            }
            if (!allBg) {
                top = y
                break
            }
        }

        if (!hasContent) return null

        // 扫描下边界
        for (y in height - 1 downTo 0) {
            yield()
            var allBg = true
            for (x in 0 until width) {
                if (!isBg(pixels[y * width + x])) {
                    allBg = false
                    break
                }
            }
            if (!allBg) {
                bottom = y
                break
            }
        }

        // 扫描左边界
        for (x in 0 until width) {
            yield()
            var allBg = true
            for (y in 0 until height) {
                if (!isBg(pixels[y * width + x])) {
                    allBg = false
                    break
                }
            }
            if (!allBg) {
                left = x
                break
            }
        }

        // 扫描右边界
        for (x in width - 1 downTo 0) {
            yield()
            var allBg = true
            for (y in 0 until height) {
                if (!isBg(pixels[y * width + x])) {
                    allBg = false
                    break
                }
            }
            if (!allBg) {
                right = x
                break
            }
        }

        val cropWidth = right - left + 1
        val cropHeight = bottom - top + 1

        // 【修复1】检查裁剪后尺寸有效性，避免创建宽高为0的Bitmap
        if (cropWidth <= 0 || cropHeight <= 0) {
            return null
        }

        // 创建裁剪后的 Bitmap（直接使用原数组的切片，避免额外复制）
        return Bitmap.createBitmap(
            pixels,
            left + top * width,   // offset
            width,                // stride（原始行宽）
            cropWidth,
            cropHeight,
            Bitmap.Config.ARGB_8888
        )
    }
}