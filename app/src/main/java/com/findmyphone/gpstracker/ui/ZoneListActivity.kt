package com.findmyphone.gpstracker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.readyGo
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.bean.ZoneEvent
import com.findmyphone.gpstracker.bean.ZoneItem
import com.findmyphone.gpstracker.databinding.ActivityZoneListBinding
import com.findmyphone.gpstracker.utils.LocationPermissionHelper
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.view.PermissionHintDialog
import com.findmyphone.gpstracker.viewmodel.ZoneListViewModel
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.jvm.java

class ZoneListActivity : BaseMvvmActivity<ActivityZoneListBinding, ZoneListViewModel>() {
    private lateinit var appAdapter: BaseQuickAdapter<ZoneItem, BaseViewHolder>
    private lateinit var locationPermissionHelper: LocationPermissionHelper
    private var permissionHintDialog: PermissionHintDialog? = null

    private var  appOpenFrom:String?=null

    private var zoneNames: MutableList<String> = mutableListOf(
        "Add your home",
        "Add your company",
        "Add your school",
        "Add your custom zone"
    )

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun init() {
        EventBus.getDefault().register(this)
        appOpenFrom = intent.getStringExtra("appOpenFrom")
        checkAndRequestPermissions()
        initAdapter()
        initViewModel()
        setupBackPressHandler()
        binding.ivBack.setOnClickListener {
            onBack()
        }
        binding.btAdd.setOnClickListener {
            LogUtil.log("click_zone_item", emptyMap())
            toZoneDetailsActivity()
        }
        viewModel.getZoonListData()
        setupNativeAd()


    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun checkAndRequestPermissions() {
        locationPermissionHelper = LocationPermissionHelper(this)
        permissionHintDialog = PermissionHintDialog(this)
        initPermissionCallback()
        initPermissionDialogListener()

    }

    private fun initPermissionCallback() {
        locationPermissionHelper.setCallback(object : LocationPermissionHelper.PermissionCallback {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
            override fun onFineLocationGranted() {
                LogUtil.log("agree_location1_permission", emptyMap())
            }
            override fun onFineLocationDenied(permanentlyDenied: Boolean) {
                LogUtil.log("disagree_location1_permission", emptyMap())
                Toast.makeText(this@ZoneListActivity, getString(R.string.fineLocation_permission_hint), Toast.LENGTH_SHORT).show()
                if (permanentlyDenied) {
                    locationPermissionHelper.navigateToAppSettings()
                }
            }
        })
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startPermissionCheckFlow() {
        if (!locationPermissionHelper.hasFineLocationPermission()) {
            permissionHintDialog?.toShow(getString(R.string.fineLocation_permission_hint), false)
        }
    }
    private fun initPermissionDialogListener() {
        permissionHintDialog?.setmOnClickListener(object : PermissionHintDialog.onClickListener {
            override fun onNext(isGetBackgroundPermission: Boolean) {
                locationPermissionHelper.checkAndRequestForeground()
            }
        })
    }




    fun toZoneDetailsActivity() {
        if (appAdapter.data.size >= 10) {
            Toast.makeText(this@ZoneListActivity, R.string.zones_max, Toast.LENGTH_SHORT).show()
        } else {
            showInterstitialAdWithStats(AreaKey.enterZoneEditAdv) {
                val item = ZoneItem(null, null, false, 40, null, "", true, true, "")
                val bundle = Bundle()
                bundle.putParcelable("zoneData", item)
                readyGo(ZoneDetailsActivity::class.java, bundle)
            }
        }
    }

    private fun setupNativeAd() {
        binding.nativeAdWidget.apply {
            setLayoutResId(R.layout.view_native_ad_four, 2)
            setAdUnitId(AreaKey.zoneListNativeAdv)
            bindToLifecycle(this@ZoneListActivity)
            // 启动自动刷新（开始加载并每隔30秒刷新一次）
            startAutoRefresh()
        }
    }



    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    fun onBack() {
        showInterstitialAdWithStats(AreaKey.zoneListBackHomeAdv) {
            if(TextUtils.isEmpty(appOpenFrom)){
                finish()
            }else{
                readyGoThenKill(MainActivity::class.java)
            }
        }
    }

    fun initAdapter() {
        appAdapter = object : BaseQuickAdapter<ZoneItem, BaseViewHolder>(R.layout.item_zone) {
            override fun convert(
                holder: BaseViewHolder,
                item: ZoneItem
            ) {
                if (item.defaultTemplate) {
                    if (zoneNames.contains(item.zoneName)) {
                        when (item.zoneName) {
                            "Add your home" -> holder.setText(
                                R.id.tv_name,
                                getString(R.string.add_your_home)
                            )

                            "Add your company" -> holder.setText(
                                R.id.tv_name,
                                getString(R.string.add_your_work)
                            )

                            "Add your school" -> holder.setText(
                                R.id.tv_name,
                                getString(R.string.add_your_school)
                            )

                            "Add your custom zone" -> holder.setText(
                                R.id.tv_name,
                                getString(R.string.add_your_customZone)
                            )
                        }
                    } else {
                        holder.setText(R.id.tv_name, item.zoneName)
                    }
                } else {
                    holder.setText(R.id.tv_name, item.zoneName)
                }
                var position = appAdapter.getItemPosition(item)
                when (position) {
                    0 -> {
                        holder.setImageResource(R.id.iv_zone, R.mipmap.ic_home_zone)
                    }

                    1 -> {
                        holder.setImageResource(R.id.iv_zone, R.mipmap.ic_work_zone)
                    }

                    2 -> {
                        holder.setImageResource(R.id.iv_zone, R.mipmap.ic_school_zone)
                    }

                    else -> {
                        holder.setImageResource(R.id.iv_zone, R.mipmap.ic_location_zone)
                    }
                }
                holder.getView<RelativeLayout>(R.id.rl_zone).setOnClickListener {
                    LogUtil.log("click_zone_item", emptyMap())
                    showInterstitialAdWithStats(AreaKey.enterZoneEditAdv){
                        val bundle = Bundle()
                        bundle.putParcelable("zoneData", item)
                        readyGo(ZoneDetailsActivity::class.java, bundle)
                    }
                }
            }

        }



        binding.rcZone.apply {
            adapter = appAdapter
            layoutManager = LinearLayoutManager(
                this@ZoneListActivity,
                RecyclerView.VERTICAL,
                false
            )
        }
        binding.rcZone.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL).apply {
                setDrawable(ContextCompat.getDrawable(this@ZoneListActivity, R.drawable.transparent_divider)!!)
            }
        )

    }


    @SuppressLint("MissingPermission")
    fun initViewModel() {
        viewModel.isLoading.observe(this) {
            handleLoadingState(it)
        }
        viewModel.zoonListData.observe(this) {
            if (it != null) {
                appAdapter.setList(it.items)
            }
            startPermissionCheckFlow()
        }


    }



    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: ZoneEvent) {
        viewModel.getZoonListData()
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        permissionHintDialog?.release()
        permissionHintDialog?.dismiss()
        super.onDestroy()
    }
}