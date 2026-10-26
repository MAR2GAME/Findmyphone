package com.findmyphone.gpstracker.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.contrarywind.adapter.WheelAdapter
import com.contrarywind.view.WheelView
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.bean.ZoneAlertItem
import com.findmyphone.gpstracker.databinding.ActivityZoneAlertBinding
import com.findmyphone.gpstracker.viewmodel.ZoneAlertViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.collections.map
import kotlin.jvm.java

class ZoneAlertActivity : BaseMvvmActivity<ActivityZoneAlertBinding, ZoneAlertViewModel>() {
    private lateinit var appAdapter: BaseQuickAdapter<ZoneAlertItem, BaseViewHolder>
    private val zoneAlertViewModel: ZoneAlertViewModel by lazy {
        ViewModelProvider(this)[ZoneAlertViewModel::class.java]
    }

    private var  appOpenFrom:String?=null
    private var zoneNames: MutableList<String> = mutableListOf("Add your home","Add your company","Add your school","Add your custom zone")
    private var page=1

    private var datePickerDialog: AlertDialog? = null

    override fun init() {
        appOpenFrom = intent.getStringExtra("appOpenFrom")
        initAdapter()
        observeDateState()
        setupBackPressHandler()
        initClickListeners()
        setupNativeAd()
        // 从 ViewModel 获取当前日期值
        val startDateStr = zoneAlertViewModel.formatDate(zoneAlertViewModel.getCurrentStartDate())
        val endDateStr = zoneAlertViewModel.formatDate(zoneAlertViewModel.getCurrentEndDate())
        viewModel.getData(page, startDateStr,endDateStr)
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    private fun setupNativeAd() {
        binding.nativeAdWidget.apply {
            setLayoutResId(R.layout.view_native_ad_four,2)
            setAdUnitId(AreaKey.zoneAlertNativeAdv)
            bindToLifecycle(this@ZoneAlertActivity)
            // 启动自动刷新（开始加载并每隔30秒刷新一次）
            startAutoRefresh()
        }
    }

    fun onBack(){
        showInterstitialAdWithStats(AreaKey.zoneAlertBackHomeAdv){
            if(TextUtils.isEmpty(appOpenFrom)){
                finish()
            }else{
                readyGoThenKill(MainActivity::class.java)
            }
        }

    }

    fun formatMillisToTimeAndDate(millis: Long): String {
        val date = Date(millis)
        // HH:mm 表示24小时制，hh:mm 表示12小时制（需搭配 a 显示上下午）
        val formatter = SimpleDateFormat("HH:mm MM/dd/yyyy", Locale.getDefault())
        return formatter.format(date)
    }
    fun initAdapter() {
        appAdapter = object : BaseQuickAdapter<ZoneAlertItem, BaseViewHolder>(R.layout.item_zone_alert) {
            override fun convert(holder: BaseViewHolder, item: ZoneAlertItem) {
                // 姓名（空值安全）
                holder.setText(R.id.tv_name, item.friendDisplayName ?: "")
                // 性别图标（when 表达式更清晰）
                val genderRes = when (item.friendGender) {
                    "male" -> R.mipmap.ic_male_check
                    "female" -> R.mipmap.ic_female_check
                    else -> R.mipmap.ic_other_check
                }
                holder.setImageResource(R.id.iv_gender, genderRes)
                // 提示文本（进入/离开区域）
                val hintText = if (!item.eventType.isNullOrEmpty() && !item.zoneName.isNullOrEmpty()) {
                    val actionText = if (item.eventType == "enter") {
                        getString(R.string.has_already_arrived)
                    } else {
                        getString(R.string.has_already_left)
                    }
                    val name=if(item.defaultTemplate){
                        if(zoneNames.contains(item.zoneName)){
                            when(item.zoneName){
                                "Add your home"->  getString(R.string.home)
                                "Add your company"-> getString(R.string.work)
                                "Add your school"->  getString(R.string.school)
                                "Add your custom zone"-> getString(R.string.custom_zone)
                                else->""
                            }
                        }else{
                            item.zoneName
                        }
                    }else{
                        item.zoneName
                    }

                    actionText+" " + name
                } else {
                    ""
                }
                holder.setText(R.id.tv_hint, hintText)
                // 时间戳格式化（使用安全调用和 Elvis 操作符）
                val timeText = item.triggeredAt?.let { formatMillisToTimeAndDate(it) } ?: ""
                holder.setText(R.id.tv_time, timeText)
            }
        }
        binding.rcAlert.apply {
            adapter = appAdapter
            layoutManager = LinearLayoutManager(
                this@ZoneAlertActivity,
                RecyclerView.VERTICAL,
                false
            )
        }
        binding.rcAlert.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL).apply {
                setDrawable(ContextCompat.getDrawable(this@ZoneAlertActivity, R.drawable.transparent_divider)!!)
            }
        )
        binding.sm.setOnRefreshListener {
            page=1
            viewModel.getData(page, binding.tvStartTime.text.toString(),binding.tvEndTime.text.toString())
        }
        binding.sm.setOnLoadMoreListener {
            page++
            viewModel.getData(page, binding.tvStartTime.text.toString(),binding.tvEndTime.text.toString())
        }
    }


    private fun observeDateState() {
        zoneAlertViewModel.startDate.observe(this) { calendar ->
            binding.tvStartTime.text = zoneAlertViewModel.formatDate(calendar)
            if(isUserSet){
                page=1
                viewModel.getData(page, binding.tvStartTime.text.toString(),binding.tvEndTime.text.toString())
            }
        }
        zoneAlertViewModel.endDate.observe(this) { calendar ->
            binding.tvEndTime.text = zoneAlertViewModel.formatDate(calendar)
            if(isUserSet){
                page=1
                viewModel.getData(page, binding.tvStartTime.text.toString(),binding.tvEndTime.text.toString())
            }
        }
        viewModel.isLoading.observe(this){
            handleLoadingState(it)
        }
        viewModel.zoneAlertData.observe(this){
            binding.sm.finishLoadMore()
            binding.sm.finishRefresh()
            if (page == 1) {
                if(it!=null){
                    it?.items?.let { appAdapter.setList(it) }
                }else{
                    appAdapter.setList(mutableListOf())
                }
            } else {
                it?.items?.let { appAdapter.addData(it) }
            }
            val totalItems = it?.total ?: 0
            if (appAdapter.data.isEmpty() || appAdapter.data.size == totalItems) {
                binding.sm.finishLoadMoreWithNoMoreData()
            } else {
                binding.sm.resetNoMoreData()
            }
            binding.ivNoData.visibility = if (appAdapter.data.isEmpty()) View.VISIBLE else View.GONE
        }
    }





    private var isUserSet=false

    private fun initClickListeners() {
        binding.ivBack.setOnClickListener { onBack() }
        binding.rlStartTime.setOnClickListener { showDatePicker(isStartPicker = true) }
        binding.rlEndTime.setOnClickListener { showDatePicker(isStartPicker = false) }
    }

    private fun showDatePicker(isStartPicker: Boolean) {
        isUserSet=true
        val minDate = createBoundaryDate(2000, Calendar.JANUARY, 1)
        val maxDate = createBoundaryDate(2100, Calendar.DECEMBER, 31)
        val rangeStart = if (isStartPicker) minDate else zoneAlertViewModel.getCurrentStartDate()
        val rangeEnd = if (isStartPicker) zoneAlertViewModel.getCurrentEndDate() else maxDate
        val selectedDate = getDisplayedDate(isStartPicker).coerceIn(rangeStart, rangeEnd)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.picker_time_custom, null)
        val wvYear = dialogView.findViewById<WheelView>(R.id.wv_year)
        val wvMonth = dialogView.findViewById<WheelView>(R.id.wv_month)
        val wvDay = dialogView.findViewById<WheelView>(R.id.wv_day)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmit)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = if (isStartPicker) getString(R.string.select_start_time) else getString(R.string.select_end_time)
        // 统一配置 WheelView 样式（不需要 setItemHeight，因为它不存在）
        fun setupWheelView(wv: WheelView) {
            wv.setCyclic(false)
            wv.setItemsVisibleCount(7)          // 显示7项（内部+2变成9项）
            wv.setTextSize(18f)                 // 字体大小18sp（因为宽度固定，不会被缩小太多）
            wv.setLineSpacingMultiplier(2.2f)   // 增大行间距，使每个item更高 → 整体高度变大
            wv.setTextColorOut(getColor(R.color.picker_text_out))
            wv.setTextColorCenter(getColor(R.color.picker_text_center))
            wv.setDividerColor(getColor(R.color.picker_divider))
            wv.setDividerType(WheelView.DividerType.FILL)
            wv.setAlphaGradient(true)
        }

        // 年份数据
        val startYear = minDate.get(Calendar.YEAR)
        val endYear = maxDate.get(Calendar.YEAR)
        val years = (startYear..endYear).map { it.toString() }
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
            setupWheelView(wvDay)  // 重新应用样式
            val curDayPos = wvDay.currentItem
            if (curDayPos >= days.size) {
                wvDay.currentItem = days.size - 1
            }
        }
        // 设置当前选中值
        val curYear = selectedDate.get(Calendar.YEAR)
        val curMonth = selectedDate.get(Calendar.MONTH) + 1
        val curDay = selectedDate.get(Calendar.DAY_OF_MONTH)

        wvYear.currentItem = curYear - startYear
        wvMonth.currentItem = curMonth - 1
        updateDays(curYear, curMonth)
        wvDay.currentItem = curDay - 1

        // 监听年月变化
        val onYearMonthChanged = {
            val year = startYear + wvYear.currentItem
            val month = wvMonth.currentItem + 1
            updateDays(year, month)
        }
        wvYear.setOnItemSelectedListener { onYearMonthChanged() }
        wvMonth.setOnItemSelectedListener { onYearMonthChanged() }

        // 确定按钮
        btnSubmit.setOnClickListener {
            val year = startYear + wvYear.currentItem
            val month = wvMonth.currentItem + 1
            val day = wvDay.currentItem + 1
            val pickedCalendar = Calendar.getInstance().apply {
                set(year, month - 1, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (isStartPicker) {
                if (pickedCalendar.after(zoneAlertViewModel.getCurrentEndDate())) {
                    Toast.makeText(this, R.string.start_time_later_than_end_time, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                zoneAlertViewModel.updateStartDate(pickedCalendar)
            } else {
                if (pickedCalendar.before(zoneAlertViewModel.getCurrentStartDate())) {
                    Toast.makeText(this, R.string.end_time_earlier_than_start_time, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                zoneAlertViewModel.updateEndDate(pickedCalendar)
            }
            datePickerDialog?.dismiss()
        }
        btnCancel.setOnClickListener { datePickerDialog?.dismiss() }
        datePickerDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        datePickerDialog?.show()
        // 设置对话框宽度为 match_parent，让 WheelView 有足够空间显示文字
        datePickerDialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun getDisplayedDate(isStartPicker: Boolean): Calendar {
        val dateText = if (isStartPicker) binding.tvStartTime.text.toString()
        else binding.tvEndTime.text.toString()
        val fallback = if (isStartPicker) zoneAlertViewModel.getCurrentStartDate()
        else zoneAlertViewModel.getCurrentEndDate()
        return zoneAlertViewModel.parseDate(dateText) ?: fallback
    }

    private fun createBoundaryDate(year: Int, month: Int, dayOfMonth: Int): Calendar {
        return Calendar.getInstance().apply {
            set(year, month, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun Calendar.coerceIn(minDate: Calendar, maxDate: Calendar): Calendar {
        return when {
            before(minDate) -> Calendar.getInstance().apply { timeInMillis = minDate.timeInMillis }
            after(maxDate) -> Calendar.getInstance().apply { timeInMillis = maxDate.timeInMillis }
            else -> Calendar.getInstance().apply { timeInMillis = this@coerceIn.timeInMillis }
        }
    }

    private fun getDaysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun dp2px(dp: Float): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    inner class ArrayWheelAdapter<T>(private val items: List<T>) : WheelAdapter<T> {
        override fun getItemsCount(): Int = items.size
        override fun getItem(index: Int): T? = items.getOrNull(index)
        override fun indexOf(o: T): Int = items.indexOf(o)
    }

    override fun onDestroy() {
        datePickerDialog?.dismiss()
        datePickerDialog = null
        super.onDestroy()
    }
}