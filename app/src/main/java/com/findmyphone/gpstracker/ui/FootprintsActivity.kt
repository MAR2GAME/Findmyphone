package com.findmyphone.gpstracker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.contrarywind.adapter.WheelAdapter
import com.contrarywind.view.WheelView
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.isNetworkAvailable
import com.findmyphone.gpstracker.bean.FootprintsListItem
import com.findmyphone.gpstracker.databinding.ActivityFootprintsBinding
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.viewmodel.FootprintsViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class FootprintsActivity : BaseMvvmActivity<ActivityFootprintsBinding, FootprintsViewModel>(),
    OnMapReadyCallback {

    companion object {
        private const val TAG = "FootprintsActivity"
        private const val DEFAULT_ZOOM = 12f
        private const val MIN_YEAR = 2000
        private const val MAX_YEAR = 2100
    }

    private lateinit var mMap: GoogleMap
    private var centerLatLng: LatLng? = null
    private var customMarkerIcon: BitmapDescriptor? = null
    private var scaledBitmap: android.graphics.Bitmap? = null
    private var marker: Marker? = null

    // ViewModel 实例（使用 by lazy 避免重复创建）


    // 日期选择器对话框引用
    private var datePickerDialog: AlertDialog? = null

    private var adapter: BaseQuickAdapter<FootprintsListItem,BaseViewHolder>?=null

    override fun init() {
        initMarkerIcon()
        initAdapter()
        observeDateState()
        initClickListeners()
        setupBackPressHandler()
        // 地图初始化
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    fun initAdapter(){
        adapter=object : BaseQuickAdapter<FootprintsListItem,BaseViewHolder>(R.layout.item_location){
            override fun convert(
                holder: BaseViewHolder,
                item: FootprintsListItem
            ) {
                val timeText = formatTimestamp(item.timestamp)
                val fullText = "$timeText  ${item.locationDetail}"
                val spannable = SpannableString(fullText)
// 设置时间部分的颜色（从0到timeText.length）
                spannable.setSpan(
                    ForegroundColorSpan(this@FootprintsActivity.getColor( R.color.save_color)),
                    0,
                    timeText.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                holder.setText(R.id.tv_info,spannable)
            }
        }

        binding.rcInfo.adapter=adapter
        binding.rcInfo.layoutManager= LinearLayoutManager(this, RecyclerView.VERTICAL, false)
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    fun onBack(){
        showInterstitialAdWithStats(AreaKey.historyBackHomeAdv){
            finish()
        }

    }



    private fun initClickListeners() {
        binding.ivBack.setOnClickListener { onBack() }
        // 点击日期选择区域（假设布局中有 tvStartTime）
        binding.tvStartTime.setOnClickListener { showSingleDatePicker() }
    }

    private fun observeDateState() {
        // 观察日期变化，更新 TextView 显示，并可根据新日期加载数据
        viewModel.selectedDate.observe(this) { calendar ->
            binding.tvStartTime.text = viewModel.formatDate(calendar)
            // 日期变化时，可在此调用加载足迹数据的方法（根据实际需求）
            onDateChanged(calendar)
        }
        viewModel.isLoading.observe(this){
            handleLoadingState(it)
        }
        viewModel.footprintsData.observe(this){
            adapter!!.setList(it)
            binding.ivNoData.isVisible= adapter!!.data.isEmpty()
        }
    }

    /**
     * 日期变化时触发（例如重新请求足迹数据）
     */
    private fun onDateChanged(calendar: Calendar) {
        Log.d(TAG, "Date changed to: ${viewModel.formatDate(calendar)}")
        // TODO: 在此调用 ViewModel 中根据日期查询足迹的方法
        // 例如：footprintsViewModel.loadFootprintsByDate(calendar)

        viewModel.getListData(viewModel.formatDate(calendar))
    }

    // ---------- 单个日期选择器（WheelView 风格） ----------
    private fun showSingleDatePicker() {
        // 当前选中的日期（用于初始化滚动位置）
        val currentDate = viewModel.getCurrentSelectedDate()

        // 最小/最大可选日期边界（最大边界仍为 MAX_YEAR，但在确认时会限制不能超过今天）
        val minDate = createBoundaryDate(MIN_YEAR, Calendar.JANUARY, 1)
        val maxDate = createBoundaryDate(MAX_YEAR, Calendar.DECEMBER, 31)
        // 将当前日期约束在边界内
        val selectedDate = currentDate.coerceIn(minDate, maxDate)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.picker_time_custom, null)
        val wvYear = dialogView.findViewById<WheelView>(R.id.wv_year)
        val wvMonth = dialogView.findViewById<WheelView>(R.id.wv_month)
        val wvDay = dialogView.findViewById<WheelView>(R.id.wv_day)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmit)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        // 统一配置 WheelView 样式
        fun setupWheelView(wv: WheelView) {
            wv.setCyclic(false)
            wv.setItemsVisibleCount(7)
            wv.setTextSize(18f)
            wv.setLineSpacingMultiplier(2.2f)
            wv.setTextColorOut(resources.getColor(R.color.picker_text_out))
            wv.setTextColorCenter(resources.getColor(R.color.picker_text_center))
            wv.setDividerColor(resources.getColor(R.color.picker_divider))
            wv.setDividerType(WheelView.DividerType.FILL)
            wv.setAlphaGradient(true)
        }

        // 年份数据
        val years = (MIN_YEAR..MAX_YEAR).map { it.toString() }
        wvYear.adapter = ArrayWheelAdapter(years)
        setupWheelView(wvYear)

        // 月份数据
        val months = (1..12).map { it.toString().padStart(2, '0') }
        wvMonth.adapter = ArrayWheelAdapter(months)
        setupWheelView(wvMonth)

        // 动态更新天数
        fun updateDays(year: Int, month: Int) {
            val maxDay = getDaysInMonth(year, month)
            val days = (1..maxDay).map { it.toString().padStart(2, '0') }
            wvDay.adapter = ArrayWheelAdapter(days)
            setupWheelView(wvDay)
            val curDayPos = wvDay.currentItem
            if (curDayPos >= days.size) {
                wvDay.currentItem = days.size - 1
            }
        }

        // 设置当前选中值
        val curYear = selectedDate.get(Calendar.YEAR)
        val curMonth = selectedDate.get(Calendar.MONTH) + 1
        val curDay = selectedDate.get(Calendar.DAY_OF_MONTH)

        wvYear.currentItem = curYear - MIN_YEAR
        wvMonth.currentItem = curMonth - 1
        updateDays(curYear, curMonth)
        wvDay.currentItem = curDay - 1

        // 监听年月变化以更新天数
        val onYearMonthChanged = {
            val year = MIN_YEAR + wvYear.currentItem
            val month = wvMonth.currentItem + 1
            updateDays(year, month)
        }
        wvYear.setOnItemSelectedListener { onYearMonthChanged() }
        wvMonth.setOnItemSelectedListener { onYearMonthChanged() }

        // ----- 确定按钮（增加限制：不能选择超过当前日期） -----
        btnSubmit.setOnClickListener {
            val year = MIN_YEAR + wvYear.currentItem
            val month = wvMonth.currentItem + 1
            val day = wvDay.currentItem + 1
            val pickedCalendar = Calendar.getInstance().apply {
                set(year, month - 1, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 获取当前日期（归一化到当天 00:00:00）
            val todayCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 校验：选择的日期不能晚于今天
            if (pickedCalendar.after(todayCalendar)) {
                Toast.makeText(this@FootprintsActivity, R.string.start_time_later_than_end_time, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.updateSelectedDate(pickedCalendar)
            datePickerDialog?.dismiss()
        }

        btnCancel.setOnClickListener { datePickerDialog?.dismiss() }

        datePickerDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        datePickerDialog?.show()
        datePickerDialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // ---------- 辅助方法（日期边界、天数、坐标等） ----------
    private fun createBoundaryDate(year: Int, month: Int, dayOfMonth: Int): Calendar {
        return Calendar.getInstance().apply {
            set(year, month, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun getDaysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun Calendar.coerceIn(minDate: Calendar, maxDate: Calendar): Calendar {
        return when {
            before(minDate) -> Calendar.getInstance().apply { timeInMillis = minDate.timeInMillis }
            after(maxDate) -> Calendar.getInstance().apply { timeInMillis = maxDate.timeInMillis }
            else -> Calendar.getInstance().apply { timeInMillis = this@coerceIn.timeInMillis }
        }
    }

    // WheelView 适配器（内部类）
    inner class ArrayWheelAdapter<T>(private val items: List<T>) : WheelAdapter<T> {
        override fun getItemsCount(): Int = items.size
        override fun getItem(index: Int): T? = items.getOrNull(index)
        override fun indexOf(o: T): Int = items.indexOf(o)
    }

    // ---------- 地图相关（原 FootprintsActivity 已有逻辑，保持不变） ----------
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun initMarkerIcon() {
        if (customMarkerIcon == null) {
            val originalBitmap = android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_map_loc)
            if (originalBitmap == null) return
            val targetWidth = 48.dpToPx()
            val targetHeight = 48.dpToPx()
            scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, false)
            customMarkerIcon = BitmapDescriptorFactory.fromBitmap(scaledBitmap!!)
            originalBitmap.recycle()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = false
        setupInfoWindowAdapter()

        mMap.setOnInfoWindowLongClickListener { marker ->
            val address = marker.snippet?.takeIf { it.isNotBlank() }
                ?: marker.title?.takeIf { it.isNotBlank() }
                ?: "(${marker.position.latitude}, ${marker.position.longitude})"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", address)
            clipboard.setPrimaryClip(clip)
        }

        val latitude = AppPrefsUtils.getDouble("latitude", 40.7580)
        val longitude = AppPrefsUtils.getDouble("longitude", -73.9855)
        if (centerLatLng == null) {
            centerLatLng = LatLng(latitude, longitude)
        }
        addMarkerAndMoveCamera(centerLatLng!!)
    }

    private fun addMarkerAndMoveCamera(latLng: LatLng) {
        marker = mMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .snippet(getString(R.string.loading_address))
                .icon(customMarkerIcon)
        )
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
        marker?.showInfoWindow()
        lifecycleScope.launch {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val address = getAddressWithGeocoder(latLng.latitude, latLng.longitude)
                val finalSnippet = if (address.isNotEmpty()) address else getString(R.string.no_address)
                marker?.snippet = finalSnippet
                if (marker?.isInfoWindowShown == true) {
                    marker?.hideInfoWindow()
                    marker?.showInfoWindow()
                }
            }
        }
    }

    private suspend fun getAddressWithGeocoder(latitude: Double, longitude: Double): String =
        withContext(Dispatchers.IO) {
            if (!isNetworkAvailable(this@FootprintsActivity)) {
                Log.w(TAG, "Network unavailable, skip geocoding")
                return@withContext ""
            }
            try {
                val geocoder = Geocoder(this@FootprintsActivity, Locale.getDefault())
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getAddressAsync(geocoder, latitude, longitude)
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.getAddressLine(0)
                }
                address ?: ""
            } catch (e: Exception) {
                Log.e(TAG, "Geocoder failed", e)
                ""
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun getAddressAsync(geocoder: Geocoder, latitude: Double, longitude: Double): String? =
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) {
                        val address = addresses.firstOrNull()?.getAddressLine(0)
                        continuation.resume(address)
                    }
                }

                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) {
                        Log.e(TAG, "Geocode error: $errorMessage")
                        continuation.resume(null)
                    }
                }
            })
            continuation.invokeOnCancellation {
                Log.d(TAG, "Geocoder request cancelled")
            }
        }

    private fun setupInfoWindowAdapter() {
        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? {
                return try {
                    val view = LayoutInflater.from(this@FootprintsActivity).inflate(R.layout.pop_loction, null, false)
                    val snippet = marker.snippet
                    val tvLoc = view.findViewById<TextView>(R.id.tv_loc)
                    tvLoc.text = if (snippet.isNullOrEmpty()) getString(R.string.no_address) else snippet
                    view
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to inflate info window", e)
                    null
                }
            }

            override fun getInfoContents(marker: Marker): View? = null
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        marker?.remove()
        marker = null
        scaledBitmap?.recycle()
        scaledBitmap = null
        customMarkerIcon = null
        datePickerDialog?.dismiss()
        datePickerDialog = null
    }
}