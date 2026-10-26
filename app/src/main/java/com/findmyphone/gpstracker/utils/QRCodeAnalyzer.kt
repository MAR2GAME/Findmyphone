package com.findmyphone.gpstracker.utils

import android.graphics.ImageFormat
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 二维码分析器（生产级）
 * - 复用 ByteArray 缓冲区，大幅减少内存分配
 * - 单线程解码，避免多帧同时处理
 * - 防抖处理，防止短时间内重复回调
 */
class QRCodeAnalyzer(
    private val onResult: (String) -> Unit,
    private val isScanningEnabled: AtomicBoolean
) : ImageAnalysis.Analyzer {

    private val tag = "QRCodeAnalyzer"
    private val analysisDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val analysisScope = CoroutineScope(analysisDispatcher + SupervisorJob())
    private val lastResultTime = AtomicLong(0L)

    // 可复用缓冲区
    private var nv21Buffer: ByteArray? = null
    private var rotatedBuffer: ByteArray? = null

    private companion object {
        private const val DEBOUNCE_TIME_MS = 1000L
    }

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isScanningEnabled.get()) {
            imageProxy.close()
            return
        }

        val image = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        if (image.format != ImageFormat.YUV_420_888) {
            imageProxy.close()
            return
        }

        val width = imageProxy.width
        val height = imageProxy.height
        if (width <= 0 || height <= 0) {
            imageProxy.close()
            return
        }

        // 复用缓冲区转换 NV21
        val nv21 = YuvUtils.yuv420888ToNv21(image, null, nv21Buffer)
        nv21Buffer = nv21
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        imageProxy.close()   // 立即释放相机帧

        if (nv21.isEmpty()) return

        analysisScope.launch {
            try {
                val (finalData, finalWidth, finalHeight) = if (rotationDegrees != 0) {
                    val rotated = YuvUtils.rotateNV21(nv21, width, height, rotationDegrees, rotatedBuffer)
                    rotatedBuffer = rotated
                    val newWidth = when (rotationDegrees) {
                        90, 270 -> height
                        else -> width
                    }
                    val newHeight = when (rotationDegrees) {
                        90, 270 -> width
                        else -> height
                    }
                    Triple(rotated, newWidth, newHeight)
                } else {
                    Triple(nv21, width, height)
                }

                val result = decode(finalData, finalWidth, finalHeight)

                if (result != null && isScanningEnabled.get()) {
                    val now = System.currentTimeMillis()
                    val last = lastResultTime.get()
                    if (now - last > DEBOUNCE_TIME_MS && lastResultTime.compareAndSet(last, now)) {
                        withContext(Dispatchers.Main) {
                            onResult(result)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(tag, "Analysis cancelled")
            } catch (e: Exception) {
                Log.e(tag, "Decode error", e)
            }
        }
    }

    private fun decode(data: ByteArray, width: Int, height: Int): String? {
        return try {
            val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            reader.decode(binaryBitmap).text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            Log.w(tag, "Decode failed: ${e.message}")
            null
        }
    }

    /** 重置防抖状态（用于重新启动扫描时） */
    fun resetScanningState() {
        lastResultTime.set(0L)
    }

    fun release() {
        analysisScope.cancel("Analyzer released")
        reader.reset()
        nv21Buffer = null
        rotatedBuffer = null
    }
}