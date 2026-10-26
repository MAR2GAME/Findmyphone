package com.findmyphone.gpstracker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.base.command.REQUEST_CODE_NOTIFICATION
import com.findmyphone.gpstracker.base.command.checkNotificationPermission
import com.findmyphone.gpstracker.base.command.hasLocationPermission
import com.findmyphone.gpstracker.base.command.rateUS
import com.findmyphone.gpstracker.base.command.readyGo
import com.findmyphone.gpstracker.bean.ActionBean
import com.findmyphone.gpstracker.databinding.ActivityMainBinding
import com.findmyphone.gpstracker.services.PersistentNotificationService
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.AppUpdateHelper
import com.findmyphone.gpstracker.utils.LocationPermissionHelper
import com.findmyphone.gpstracker.utils.LocationReporterManager
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.utils.SafeChecker
import com.findmyphone.gpstracker.view.ExitDialog
import com.findmyphone.gpstracker.view.NotificationDialog
import com.findmyphone.gpstracker.view.PermissionHintDialog
import com.findmyphone.gpstracker.view.RateUsDialog
import com.findmyphone.gpstracker.viewmodel.MianViewModel
import com.google.android.play.core.install.model.AppUpdateType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import java.io.ByteArrayOutputStream

class MainActivity : BaseMvvmActivity<ActivityMainBinding, MianViewModel>() {
    companion object {
        private const val TAG = "MainActivity"
    }
    private lateinit var actionAdapter: BaseQuickAdapter<ActionBean, BaseViewHolder>
    private var notificationDialog: NotificationDialog? = null
    private val actionItems = mutableListOf<ActionBean>()

    private lateinit var appUpdateHelper: AppUpdateHelper

    private var exitDialog: ExitDialog? = null



    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            LogUtil.log("agree_push_permission", emptyMap())
            startPersistentService()
        } else {
            LogUtil.log("disagree_push_permission", emptyMap())
            Toast.makeText(this, getString(R.string.notification_dialog_hint), Toast.LENGTH_SHORT)
                .show()
            val isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (isPermanentlyDenied) {
                openAppSettings()
            }
        }
    }

    override fun init() {
        initUpdate()
        setupBackPressHandler()
        setupActionGrid()
        checkPermissionAndStart()
        initViewModel()
        setupClickListeners()
        viewModel.saveFCMToken()
       // doCheck()
        LogUtil.log("enter_homepage", emptyMap())
    }

    fun doCheck() {
        lifecycleScope.launch(Dispatchers.IO) {
            val passed = SafeChecker.performEssentialCheck(this@MainActivity)
            if (!passed) {
                withContext(Dispatchers.Main) {
                    SafeChecker.forceExit()
                }
            }
        }
    }




    private fun initViewModel() {
        viewModel.isLoading.observe(this) { showLoading ->
            handleLoadingState(showLoading)
        }
        viewModel.codeData.observe(this) {
            if (!TextUtils.isEmpty(it.code) && it.bitmap != null) {
                showInterstitialAdWithStats(AreaKey.enterShareCodeAdv){
                    val bundle = Bundle()
                    val stream = ByteArrayOutputStream()
                    it.bitmap!!.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        stream
                    ) // 可选用JPEG，大小更小
                    val byteArray = stream.toByteArray()
                    bundle.putByteArray("bitmap_key", byteArray)
                    bundle.putString("code", it.code)
                    readyGo(ShareMyCodeActivity::class.java, bundle)
                }
            }
        }

    }
    private var rateUsDialog: RateUsDialog?=null
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(AppPrefsUtils.getBoolean("isFirstExit",true)){
                    if(rateUsDialog==null){
                        rateUsDialog= RateUsDialog(this@MainActivity)
                        rateUsDialog!!.setOnRateClickListener(object : RateUsDialog.OnRateClickListener{
                            override fun onRateClicked(rating: Int) {
                                Toast.makeText(this@MainActivity, getString(R.string.feedback), Toast.LENGTH_SHORT)
                                    .show()
                                if(rating==5){
                                    rateUS()
                                }
                                rateUsDialog!!.dismiss()

                            }
                        })
                    }
                    AppPrefsUtils.commitBoolean("isFirstExit",false)
                    LogUtil.log("rate_popup", emptyMap())
                    rateUsDialog!!.showDialog()
                }else{
                    if (exitDialog == null) {
                        exitDialog = ExitDialog(this@MainActivity)
                        exitDialog!!.setmOnClickListener(object : ExitDialog.onClickListener {
                            override fun onNext() {
                                finish()
                            }
                        })
                    }
                    exitDialog!!.toShow()
                }
            }
        })
    }

    fun initUpdate(){
        appUpdateHelper = AppUpdateHelper(
            context = this.applicationContext,
            coroutineScope = lifecycleScope,
            updateRequestCode = AppUpdateHelper.UPDATE_REQUEST_CODE
        )
    }


    fun setupClickListeners(){
        binding.ivTracker.setOnClickListener {
            showInterstitialAdWithStats(AreaKey.enterInputCodeAdv) {
                readyGo(AddFriendActivity::class.java)
            }
        }
        binding.ivLiveLocation.setOnClickListener {
            readyGo(LiveLocationActivity::class.java)
        }
        binding.ivSetting.setOnClickListener {
            readyGo(SettingActivity::class.java)
        }
    }

    // ---------- 权限处理 ----------
    private fun checkPermissionAndStart() {
        if(checkNotificationPermission(this)){
            startPersistentService()
        }else{
            showNotificationRationaleDialog()
        }
        if(hasLocationPermission()){
            startLocationReporter()
        }
    }

    override fun onResume() {
        super.onResume()
        // 优先检查即时更新（强制更新）
        appUpdateHelper.setCurrentActivity(this)
        appUpdateHelper.onResume(this)
        appUpdateHelper.checkAndStartUpdate(this, AppUpdateType.IMMEDIATE)

    }

    override fun onPause() {
        super.onPause()
        appUpdateHelper.clearCurrentActivity()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationReporter() {
        LocationReporterManager.setHighFrequency(true)   // 前台高频
        LocationReporterManager.start(applicationContext)
    }
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }

    // ---------- 通知对话框 ----------
    private fun showNotificationRationaleDialog() {
        if (notificationDialog == null) {
            notificationDialog = NotificationDialog(this)
            notificationDialog?.setOnClickListener(object : NotificationDialog.onClickListener{
                @RequiresApi(Build.VERSION_CODES.TIRAMISU)
                override fun onAllow() {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

            })
        }
        notificationDialog?.toShow()
    }

    // ---------- 启动前台服务 ----------
    @SuppressLint("MissingPermission") // 权限已在外部校验
    private fun startPersistentService() {
        if (PersistentNotificationService.isRunning) {
            Log.d(TAG, "Persistent service already running")
            return
        }
        try {
            val serviceIntent = Intent(this, PersistentNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start persistent service", e)
        }
    }

    // ---------- 功能列表 ----------
    private fun setupActionGrid() {
        initActionData()
        actionAdapter = object : BaseQuickAdapter<ActionBean, BaseViewHolder>(R.layout.item_action) {
            override fun convert(holder: BaseViewHolder, item: ActionBean) {
                holder.setText(R.id.tv_action, item.name)
                holder.setText(R.id.tv_action_hint, item.hint)
                holder.setImageResource(R.id.iv_action, item.res)
                holder.getView<ConstraintLayout>(R.id.content).setOnClickListener {
                    when(actionAdapter.getItemPosition(item)){
                        0->{
                            viewModel.getUserCode()
                        }
                        1->{
                            LogUtil.log("click_setup_zone_btn", emptyMap())
                            showInterstitialAdWithStats(AreaKey.enterZoneListAdv) {
                                readyGo(ZoneListActivity::class.java)
                            }
                        }
                        2->{
                            LogUtil.log("click_phone_number",emptyMap())
                            showInterstitialAdWithStats(AreaKey.enterPhoneSearchAdv) {
                              readyGo(SearchPhoneActivity::class.java)
                            }
                        }
                        3->{
                            readyGo(NearbyPlacesActivity::class.java)
                        }
                    }

                }
            }
        }
        binding.rcAction.apply {
            adapter = actionAdapter
            layoutManager = GridLayoutManager(this@MainActivity, 2)
        }
        actionAdapter.setList(actionItems)
    }

    private fun initActionData() {
        actionItems.clear()
        actionItems.add(
            ActionBean(
                R.mipmap.ic_action_1,
                getString(R.string.share_secure_code),
                getString(R.string.share_secure_code_action_hint)
            )
        )
        actionItems.add(
            ActionBean(
                R.mipmap.ic_action_2,
                getString(R.string.zone_alert),
                getString(R.string.set_zone_hint)
            )
        )
        actionItems.add(
            ActionBean(
                R.mipmap.ic_action_3,
                getString(R.string.phone_number_location),
                getString(R.string.phone_number_location_action_hint)
            )
        )
        actionItems.add(
            ActionBean(
                R.mipmap.ic_action_4,
                getString(R.string.nearby_places),
                getString(R.string.nearby_places_hint)
            )
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startBackgroundReporting() {
        // 确保全局上报正在运行（如果尚未启动，则启动）
        LocationReporterManager.setHighFrequency(false)
        if (!LocationReporterManager.isRunning()) {
            LocationReporterManager.start(applicationContext)
        }
        val intent = Intent(PersistentNotificationService.ACTION_START_BG_REPORTING)
        sendBroadcast(intent)
    }

    // ---------- 生命周期 ----------
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onDestroy() {
        appUpdateHelper.cleanup()
        if (hasLocationPermission(true) && PersistentNotificationService.isRunning) {
            startBackgroundReporting()
        } else {
            LocationReporterManager.stop()
        }
        exitDialog?.release()
        exitDialog = null
        rateUsDialog?.release()
        rateUsDialog=null
        notificationDialog?.release() // 或 release()，取决于实现
        notificationDialog = null
        super.onDestroy()
    }
}