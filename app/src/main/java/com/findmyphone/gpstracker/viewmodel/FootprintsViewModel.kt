package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.FootprintsList
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpException

import kotlinx.coroutines.launch
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.apply
import kotlin.text.format
import kotlin.to

class FootprintsViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        private const val DATE_PATTERN = "MM/dd/yyyy"
    }

    // 当前选中的日期（已归一化到当天 00:00:00）
    private val _selectedDate = MutableLiveData<Calendar>()
    val selectedDate: LiveData<Calendar> = _selectedDate

    val isLoading = MutableLiveData<Boolean>()

    var footprintsData=MutableLiveData<FootprintsList>(FootprintsList())

    var utcOffsetMinutes:Int=0

    init {
        // 默认设置为当前日期（归一化后）
        _selectedDate.value = normalize(Calendar.getInstance())
        val timeZone = TimeZone.getDefault()
        // 获取当前时刻的实际偏移（含夏令时），单位毫秒
        val offsetMillis = timeZone.getOffset(System.currentTimeMillis())
        utcOffsetMinutes=offsetMillis / 60_000
    }

    /**
     * 更新选中的日期（会自动归一化并通知观察者）
     */
    fun updateSelectedDate(newDate: Calendar) {
        _selectedDate.value = normalize(newDate)
    }

    /**
     * 格式化日期为 "MM/dd/yyyy"
     */
    fun formatDate(calendar: Calendar): String {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.getDefault(), "%02d/%02d/%04d", month, day, year)
    }

    /**
     * 解析 "MM/dd/yyyy" 格式的字符串为 Calendar
     */
    fun parseDate(dateText: String): Calendar? {
        return try {
            val sdf = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(dateText) ?: return null
            normalize(Calendar.getInstance().apply { time = date })
        } catch (_: ParseException) {
            null
        }
    }

    /**
     * 获取当前选中的日期（副本）
     */
    fun getCurrentSelectedDate(): Calendar {
        return copyOf(_selectedDate.value ?: normalize(Calendar.getInstance()))
    }

    // ---------- 私有辅助方法 ----------
    private fun normalize(calendar: Calendar): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = calendar.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun copyOf(calendar: Calendar): Calendar {
        return normalize(calendar)
    }



    fun getListData(date:String){
        viewModelScope.launch {
            isLoading.value=true
            try {
                val mutableMap =
                    mutableMapOf("date" to date, "utcOffsetMinutes" to utcOffsetMinutes)
                val data =
                    HttpClient.getForData<FootprintsList>(AppConfig.BASE_URL + "/api/v1/locations/footprints",mutableMap)
                if(data!=null){
                    footprintsData.value=data
                }else{
                    footprintsData.value= FootprintsList()
                }
            } catch (e: HttpException) {
                footprintsData.value = FootprintsList()
                Toast.makeText(application,
                    application.getString(R.string.request_error), Toast.LENGTH_SHORT)
                    .show()
                // 处理 HTTP 错误
            } catch (e: IOException) {
                // 处理网络错误
                Toast.makeText(
                    application,
                    application
                        .getString(R.string.check_your_network),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    application,
                    application.getString(R.string.request_error),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isLoading.value = false
            }

        }









    }



}