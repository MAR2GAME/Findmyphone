package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.ZoneAlertIData
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.request.HttpClient
import com.findmyphone.gpstracker.request.HttpException


import kotlinx.coroutines.launch
import java.io.IOException

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.apply
import kotlin.text.format
import kotlin.to

class ZoneAlertViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        private const val DATE_PATTERN = "MM/dd/yyyy"
        private const val DEFAULT_RANGE_DAYS = 7
    }

    val isLoading = MutableLiveData<Boolean>()


    val zoneAlertData = MutableLiveData<ZoneAlertIData?>()



    private val _startDate = MutableLiveData<Calendar>()
    val startDate: LiveData<Calendar> = _startDate

    private val _endDate = MutableLiveData<Calendar>()
    val endDate: LiveData<Calendar> = _endDate

    init {
        val endCalendar = normalize(Calendar.getInstance())
        val startCalendar = copyOf(endCalendar).apply {
            add(Calendar.DAY_OF_MONTH, -DEFAULT_RANGE_DAYS)
        }
        _startDate.value = startCalendar
        _endDate.value = endCalendar
    }


    fun getData(page:Int,fromDate:String,toDate:String){
        viewModelScope.launch {
            isLoading.value = true
            try {
                val mutableMap = mutableMapOf(
                    "page" to page,
                    "size" to 15,
                    "fromDate" to fromDate,
                    "toDate" to toDate
                )
                val alertData =
                    HttpClient.getForData<ZoneAlertIData>(AppConfig.BASE_URL + "/api/v1/zones/alert-history",mutableMap)
                Log.e("weiyu","alertData"+alertData.toString())
                zoneAlertData.value=alertData
            } catch (e: HttpException) {
                zoneAlertData.value=null
                Toast.makeText(application,
                    application.getString(R.string.request_error), Toast.LENGTH_SHORT)
                    .show()
                // 处理 HTTP 错误
            } catch (e: IOException) {
                zoneAlertData.value=null
                // 处理网络错误
                Toast.makeText(
                    application,
                    application
                        .getString(R.string.check_your_network),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                zoneAlertData.value=null
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

    fun updateStartDate(newStart: Calendar) {
        val normalizedStart = normalize(newStart)
        val currentEnd = getCurrentEndDate()
        if (normalizedStart.after(currentEnd)) {
            _endDate.value = copyOf(normalizedStart)
        }
        _startDate.value = normalizedStart
    }

    fun updateEndDate(newEnd: Calendar) {
        val normalizedEnd = normalize(newEnd)
        val currentStart = getCurrentStartDate()
        if (normalizedEnd.before(currentStart)) {
            _startDate.value = copyOf(normalizedEnd)
        }
        _endDate.value = normalizedEnd
    }

//    fun formatDate(calendar: Calendar): String {
//        return createDateFormat().format(calendar.time)
//    }

    fun formatDate(calendar: Calendar): String {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1   // Calendar.MONTH 从0开始
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.getDefault(), "%02d/%02d/%04d", month, day, year)
    }



    fun parseDate(dateText: String): Calendar? {
        return try {
            val date = createDateFormat().parse(dateText) ?: return null
            normalize(Calendar.getInstance().apply { time = date })
        } catch (_: ParseException) {
            null
        }
    }

    fun getCurrentStartDate(): Calendar {
        return copyOf(
            _startDate.value ?: Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -DEFAULT_RANGE_DAYS)
            }
        )
    }

    fun getCurrentEndDate(): Calendar {
        return copyOf(_endDate.value ?: Calendar.getInstance())
    }

    private fun createDateFormat(): SimpleDateFormat {
        return SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).apply {
            isLenient = false
        }
    }

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
}
