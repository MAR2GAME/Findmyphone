package com.findmyphone.gpstracker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.bumptech.glide.Glide
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.base.command.copyText
import com.findmyphone.gpstracker.base.command.shareText
import com.findmyphone.gpstracker.databinding.ActivityShareMyCodeBinding
import com.findmyphone.gpstracker.utils.LocationPermissionHelper
import com.findmyphone.gpstracker.view.PermissionHintDialog

class ShareMyCodeActivity : BaseViewActivity<ActivityShareMyCodeBinding>() {

    companion object {
        private const val APP_SHARE_LINK = "https://play.google.com/store/apps/details?id=com.findmyphone.gpstracker"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_BITMAP = "bitmap_key"
    }

    private var isChecked = true
    private lateinit var locationPermissionHelper: LocationPermissionHelper
    private var permissionHintDialog: PermissionHintDialog? = null

    override fun initializeActivity() {
        super.initializeActivity()

        // 安全获取传递数据
        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        val bitmapBytes = intent.getByteArrayExtra(EXTRA_BITMAP)
        // 使用 Glide 直接加载字节数组，避免手动解码造成内存泄漏
        if (bitmapBytes != null && bitmapBytes.isNotEmpty()) {
            Glide.with(this)
                .load(bitmapBytes)
                .into(binding.ivCode)
        }
        binding.tvCode.text = code
        binding.ivBack.setOnClickListener {
            onBack()
        }
        binding.ivCheck.setOnClickListener {
            isChecked = !isChecked
            binding.ivCheck.setImageResource(
                if (isChecked) R.mipmap.iv_check else R.mipmap.iv_check_nor
            )
        }

        binding.ivShare.setOnClickListener {
            if (code.isNotEmpty()) shareText(code)
        }

        binding.btCopy.setOnClickListener {
            shareText(APP_SHARE_LINK)
        }

        binding.ivCopy.setOnClickListener {
            if (code.isNotEmpty()) copyText(this, code)
        }
        locationPermissionHelper = LocationPermissionHelper(this)
        handleLocationPermission()
        setupBackPressHandler()
    }

    /**
     * 处理位置权限：
     * - 有前台权限时，若无后台权限则展示引导弹窗；
     * - 无前台权限的情况交由 LocationPermissionHelper 内部自行处理。
     */
    private fun handleLocationPermission() {
        if (locationPermissionHelper.hasFineLocationPermission()) {
            if (!locationPermissionHelper.hasBackgroundPermission()) {
                showBackgroundPermissionHint()
            }
        }
    }

    private fun showBackgroundPermissionHint() {
        if (permissionHintDialog == null) {
            permissionHintDialog = PermissionHintDialog(this).apply {
                setmOnClickListener(object : PermissionHintDialog.onClickListener {
                    override fun onNext(isGetBackgroundPermission: Boolean) {
                        locationPermissionHelper.requestBackgroundPermission()
                    }
                })
            }
        }
        permissionHintDialog?.toShow(getString(R.string.backgroundLocation_permission_hint), true)
        locationPermissionHelper.setCallback(object : LocationPermissionHelper.PermissionCallback {
            override fun onBackgroundLocationDenied(notAlways: Boolean, permanentlyDenied: Boolean) {
                Toast.makeText(
                    this@ShareMyCodeActivity,
                    getString(R.string.open_permission_hint),
                    Toast.LENGTH_SHORT
                ).show()
                if (permanentlyDenied) {
                    locationPermissionHelper.navigateToAppSettings()
                }
            }
        })
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    private fun onBack() {
        showInterstitialAdWithStats(AreaKey.shareCodeBackHomeAdv) {
            finish()
        }
    }

    override fun onDestroy() {
        // 移除权限回调防止内存泄漏
        locationPermissionHelper.setCallback(null)
        permissionHintDialog?.release()
        permissionHintDialog = null
        super.onDestroy()
    }
}