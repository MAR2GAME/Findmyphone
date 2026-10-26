package com.findmyphone.gpstracker.ui

import ScannerViewModel
import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.databinding.ActivityScannerBinding
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.utils.QRCodeAnalyzer
import com.findmyphone.gpstracker.view.NoPermissionDialog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScannerActivity : BaseMvvmActivity<ActivityScannerBinding, ScannerViewModel>() {

    // 专用分析线程，避免阻塞公共线程池
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var imageAnalysis: ImageAnalysis? = null
    private val isScanningEnabled = AtomicBoolean(true)
    private var scanAnimator: ObjectAnimator? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentAnalyzer: QRCodeAnalyzer? = null
    private var noPermissionDialog: NoPermissionDialog? = null

    private var code = ""

    // 无效码去重
    private var lastInvalidCode: String? = null
    private var lastInvalidTime = 0L
    private val invalidCooldownMs = 2000L

    override fun init() {
        applyWindowInsets()
        initViewModel()
        requestCameraPermission()
        binding.rlBack.setOnClickListener { finish() }
        LogUtil.log("enter_camera", emptyMap())
    }

    // ==================== 布局适配 ====================
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rlBack) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, statusBarHeight, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    // ==================== 权限处理 ====================
    private fun hasCameraPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) initScanAreaAndStartCamera()
        else showPermissionDeniedDialog()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasCameraPermission()) {
            initScanAreaAndStartCamera()
        } else {
            Toast.makeText(this, R.string.open_permission_hint, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun requestCameraPermission() {
        if (hasCameraPermission()) {
            initScanAreaAndStartCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showPermissionDeniedDialog() {
        LogUtil.log("disagree_camera_permission", emptyMap())
        Toast.makeText(this, R.string.please_grant_camera_permissions, Toast.LENGTH_SHORT).show()
        if (noPermissionDialog == null) {
            noPermissionDialog = NoPermissionDialog(this)
            noPermissionDialog?.setmOnClickListener(object :
                NoPermissionDialog.onClickListener {
                override fun onNext(isGetBackgroundPermission: Boolean) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${packageName}")
                    }
                    settingsLauncher.launch(intent)
                }
            })
        }
        noPermissionDialog?.toShow()
    }

    // ==================== 相机初始化 ====================
    private fun initScanAreaAndStartCamera() {
        LogUtil.log("agree_camera_permission", emptyMap())
        binding.fl.post {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                startScanAnimation()
                lifecycleScope.launch(Dispatchers.Main) {
                    startCamera()
                }
            }
        }
    }

    private fun startScanAnimation() {
        // 防止重复创建动画
        scanAnimator?.cancel()
        val targetY = binding.fl.height.toFloat()
        scanAnimator = ObjectAnimator.ofFloat(binding.rectangle5, "translationY", 0f, targetY).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private suspend fun startCamera() {
        if (cameraProvider != null) return
        try {
            val provider = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(this@ScannerActivity).await()
            }
            cameraProvider = provider

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(480, 480))
                .build()

            val analyzer = QRCodeAnalyzer(
                onResult = { handleScanResult(it) },
                isScanningEnabled = isScanningEnabled
            )
            currentAnalyzer = analyzer
            analysis.setAnalyzer(analysisExecutor, analyzer)
            imageAnalysis = analysis

            provider.unbindAll()
            provider.bindToLifecycle(
                this@ScannerActivity,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (e: Exception) {
            Toast.makeText(this, R.string.camera_failed, Toast.LENGTH_SHORT).show()
            scanAnimator?.cancel()
            scanAnimator = null
            finish()
        }
    }

    // ==================== 扫描结果处理（原子操作防竞态） ====================
    private fun handleScanResult(scannedCode: String) {
        // 使用原子操作确保只有一个线程能进入处理逻辑
        if (scannedCode.length == 6) {
            // compareAndSet(true, false) 成功表示之前是 true，现在成功置为 false
            if (!isScanningEnabled.compareAndSet(true, false)) return
            imageAnalysis?.clearAnalyzer()
            code = scannedCode
            viewModel.searchFriend(code)
        } else {
            // 无效码防抖
            val now = System.currentTimeMillis()
            if (scannedCode == lastInvalidCode && (now - lastInvalidTime) < invalidCooldownMs) {
                return
            }
            lastInvalidCode = scannedCode
            lastInvalidTime = now
            Toast.makeText(this, R.string.scanning_error, Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== ViewModel 观察 ====================
    private fun initViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            handleLoadingState(loading)
        }

        viewModel.isCheckState.observe(this) { success ->
            if (success) {
                showInterstitialAdWithStats(AreaKey.enterDisplayNameAdv) {
                    ActivityManagerUtils.finishTopActivity(AddFriendActivity::class.java)
                    val bundle = Bundle().apply {
                        putString("code", code)
                        putInt("type", 1)
                    }
                    readyGoThenKill(EditFriendActivity::class.java, bundle)
                }
            } else {
                handleLoadingState(false)
                Toast.makeText(
                    this,
                    viewModel.errorMessage.value ?: getString(R.string.scanning_error),
                    Toast.LENGTH_SHORT
                ).show()
                restoreScanning()
            }
        }
    }

    private fun restoreScanning() {
        // 恢复扫描状态：重置标志并重新绑定分析器
        isScanningEnabled.set(true)
        currentAnalyzer?.resetScanningState()
        currentAnalyzer?.let {
            imageAnalysis?.setAnalyzer(analysisExecutor, it)
        }
    }

    // ==================== 生命周期处理 ====================
    override fun onResume() {
        super.onResume()
        // 移除 onResume 中的直接 finish，避免与权限回调冲突
        if (!hasCameraPermission()) {
            // 仅作提示，不关闭页面，保留用户从设置返回后的处理机会
            Toast.makeText(this, R.string.please_grant_camera_permissions, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        isScanningEnabled.set(false)
        currentAnalyzer?.release()
        currentAnalyzer = null
        noPermissionDialog?.dismiss()
        noPermissionDialog = null
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.shutdownNow()
        }
        scanAnimator?.cancel()
        scanAnimator = null
        super.onDestroy()
    }
}