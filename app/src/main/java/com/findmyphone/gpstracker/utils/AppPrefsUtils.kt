package com.findmyphone.gpstracker.utils

import android.content.Context
import android.content.SharedPreferences
import com.findmyphone.gpstracker.base.MyApplication
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object AppPrefsUtils {
    val sp: SharedPreferences by lazy {
        MyApplication.instance.getSharedPreferences(
            "find_phone_sp",
            Context.MODE_PRIVATE
        )
    }

    fun removeByKey(key: String) {
        finishEdit(sp.edit().remove(key))
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        sp.getBoolean(key, defaultValue)

    fun getString(key: String, defaultValue: String = ""): String =
        sp.getString(key, defaultValue) ?: defaultValue

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        sp.getLong(key, defaultValue)

    fun getInt(key: String, defaultValue: Int = 0): Int =
        sp.getInt(key, defaultValue)

    fun getFloat(key: String, defaultValue: Float = 0f): Float =
        sp.getFloat(key, defaultValue)

    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        return try {
            sp.getString(key, null)?.toDouble() ?: defaultValue
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    class PreferenceDelegate<T>(
        private val key: String,
        private val defaultValue: T
    ) : ReadWriteProperty<Any?, T> {

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            @Suppress("UNCHECKED_CAST")
            return when (defaultValue) {
                is Int -> getInt(key, defaultValue) as T
                is Long -> getLong(key, defaultValue) as T
                is Boolean -> getBoolean(key, defaultValue) as T
                is Float -> getFloat(key, defaultValue) as T
                is String -> getString(key, defaultValue) as T
                is Double -> getDouble(key, defaultValue) as T
                else -> throw IllegalArgumentException("Unsupported type.")
            }
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            when (value) {
                is Int -> commitInt(key, value)
                is Long -> commitLong(key, value)
                is Boolean -> commitBoolean(key, value)
                is Float -> commitFloat(key, value)
                is String -> commitString(key, value)
                is Double -> commitDouble(key, value)
                else -> throw IllegalArgumentException("Unsupported type.")
            }
        }
    }

    fun finishEdit(
        editor: SharedPreferences.Editor,
        commitImmediately: Boolean = false
    ): Boolean {
        return if (commitImmediately) {
            editor.commit()
        } else {
            editor.apply()
            true
        }
    }

    fun putInt(key: String, value: Int) {
        finishEdit(sp.edit().putInt(key, value))
    }

    fun putLong(key: String, value: Long) {
        finishEdit(sp.edit().putLong(key, value))
    }

    fun putBoolean(key: String, value: Boolean) {
        finishEdit(sp.edit().putBoolean(key, value))
    }

    fun putFloat(key: String, value: Float) {
        finishEdit(sp.edit().putFloat(key, value))
    }

    fun putString(key: String, value: String) {
        finishEdit(sp.edit().putString(key, value))
    }

    fun putDouble(key: String, value: Double) {
        finishEdit(sp.edit().putString(key, value.toString()))
    }

    fun commitInt(key: String, value: Int) = putInt(key, value)
    fun commitLong(key: String, value: Long) = putLong(key, value)
    fun commitBoolean(key: String, value: Boolean) = putBoolean(key, value)
    fun commitFloat(key: String, value: Float) = putFloat(key, value)
    fun commitString(key: String, value: String) = putString(key, value)
    fun commitDouble(key: String, value: Double) = putDouble(key, value)

    fun commitIntNow(key: String, value: Int): Boolean =
        finishEdit(sp.edit().putInt(key, value), commitImmediately = true)

    fun commitLongNow(key: String, value: Long): Boolean =
        finishEdit(sp.edit().putLong(key, value), commitImmediately = true)

    fun commitBooleanNow(key: String, value: Boolean): Boolean =
        finishEdit(sp.edit().putBoolean(key, value), commitImmediately = true)

    fun commitFloatNow(key: String, value: Float): Boolean =
        finishEdit(sp.edit().putFloat(key, value), commitImmediately = true)

    fun commitStringNow(key: String, value: String): Boolean =
        finishEdit(sp.edit().putString(key, value), commitImmediately = true)

    fun commitDoubleNow(key: String, value: Double): Boolean =
        finishEdit(sp.edit().putString(key, value.toString()), commitImmediately = true)
}
