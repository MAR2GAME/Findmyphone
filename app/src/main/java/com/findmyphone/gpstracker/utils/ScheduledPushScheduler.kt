package com.findmyphone.gpstracker.utils

import android.content.Context
import android.text.format.DateFormat
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager


import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.collections.asSequence
import kotlin.collections.forEach
import kotlin.run
import kotlin.sequences.distinct
import kotlin.sequences.mapNotNull
import kotlin.sequences.sorted
import kotlin.sequences.toList
import kotlin.text.format
import kotlin.text.isEmpty
import kotlin.text.padStart
import kotlin.text.toIntOrNull
import kotlin.text.trim
import kotlin.text.uppercase

object ScheduledPushScheduler {
    private const val TAG = "FM_ScheduledPush"
    const val KEY_TIME = "fm_scheduled_time"

    private const val TIME_REGEX_12H = """^(\d{1,2})(?::(\d{2}))?\s*(AM|PM)$"""
    private const val TIME_REGEX_24H = """^(\d{1,2}):(\d{2})$"""

    private val timePattern12H = Regex(TIME_REGEX_12H, RegexOption.IGNORE_CASE)
    private val timePattern24H = Regex(TIME_REGEX_24H)

    fun applyRemoteTimes(context: Context, raw: List<String>) {
        val times = parseTimes(context, raw)
        val workManager = WorkManager.getInstance(context)
        try {
            workManager.cancelAllWorkByTag(TAG)
            if (times.isEmpty()) {
                return
            }
            times.forEach { time ->
                scheduleNotification(context, time, workManager)
            }

        } catch (e: Exception) {
        }
    }

    fun cancelAllWork(context: Context) {
        try {
            WorkManager.getInstance(context)
                .cancelAllWorkByTag(TAG)
        } catch (e: Exception) {
        }
    }

    fun scheduleNextDay(context: Context, timeStr: String) {
        val time = parseTime(context, timeStr) ?: run {
            return
        }
        scheduleNotification(context, time, WorkManager.getInstance(context), true)
    }

    private fun scheduleNotification(
        context: Context,
        time: LocalTime,
        workManager: WorkManager,
        nextDay: Boolean = false
    ) {
        val timeStr = formatTime(context, time)
        val delayMillis = computeDelayMillis(context, time, nextDay)
        if (delayMillis <= 0) {
            return
        }
        try {
            val request = buildWorkRequest(timeStr, delayMillis)
            val workName = generateWorkName(time)

            workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                request
            )
        } catch (e: Exception) {

        }
    }

    private fun buildWorkRequest(timeStr: String, delayMillis: Long) =
        OneTimeWorkRequestBuilder<ScheduledNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .setInputData(
                Data.Builder()
                    .putString(KEY_TIME, timeStr)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                30_000L, // 30 seconds
                TimeUnit.MILLISECONDS
            )
            .build()

    private fun generateWorkName(time: LocalTime): String {
        return "ScheduledPush-${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    }

    fun parseTimes(context: Context, raw: List<String>): List<LocalTime> {
        return raw.asSequence()
            .mapNotNull { parseTime(context, it) }
            .distinct()
            .sorted()
            .toList()
    }

    fun parseTime(context: Context, input: String): LocalTime? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        return try {
            when {
                timePattern12H.matches(trimmed) -> parse12HourTime(trimmed)
                timePattern24H.matches(trimmed) -> parse24HourTime(trimmed)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse12HourTime(timeStr: String): LocalTime? {
        val match = timePattern12H.matchEntire(timeStr) ?: return null

        val hour12 = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val amPm = match.groupValues[3].uppercase()

        if (hour12 !in 1..12 || minute !in 0..59) return null

        return when (amPm) {
            "AM" -> LocalTime.of(if (hour12 == 12) 0 else hour12, minute)
            "PM" -> LocalTime.of(if (hour12 == 12) 12 else hour12 + 12, minute)
            else -> null
        }
    }

    private fun parse24HourTime(timeStr: String): LocalTime? {
        val match = timePattern24H.matchEntire(timeStr) ?: return null

        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null

        return if (hour in 0..23 && minute in 0..59) {
            LocalTime.of(hour, minute)
        } else {
            null
        }
    }

    fun formatTime(context: Context, time: LocalTime): String {
        return if (DateFormat.is24HourFormat(context)) {
            format24HourTime(time)
        } else {
            format12HourTime(time)
        }
    }

    private fun format24HourTime(time: LocalTime): String {
        return String.format("%02d:%02d", time.hour, time.minute)
    }

    private fun format12HourTime(time: LocalTime): String {
        val hour12 = when (time.hour) {
            0, 12 -> 12
            in 1..12 -> time.hour
            else -> time.hour - 12
        }
        val amPm = if (time.hour < 12) "AM" else "PM"

        return String.format("%d:%02d %s", hour12, time.minute, amPm)
    }

    private fun computeDelayMillis(
        context: Context,
        time: LocalTime,
        nextDay: Boolean
    ): Long {
        val now = LocalDateTime.now()
        val today = LocalDate.now()

        val targetDateTime = when {
            nextDay -> LocalDateTime.of(today.plusDays(1), time)
            else -> {
                val candidate = LocalDateTime.of(today, time)
                if (candidate.isAfter(now)) candidate
                else LocalDateTime.of(today.plusDays(1), time)
            }
        }

        return try {
            val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val targetMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            kotlin.comparisons.maxOf(targetMillis - nowMillis, 0)
        } catch (e: Exception) {
            0L
        }
    }
}