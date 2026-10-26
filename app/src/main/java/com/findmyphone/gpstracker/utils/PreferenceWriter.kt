package com.findmyphone.gpstracker.utils

import android.content.SharedPreferences

class PreferenceWriter(
    private val preferences: SharedPreferences
) {
    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    fun removeSync(key: String): Boolean {
        return preferences.edit().remove(key).commit()
    }

    fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    fun putIntSync(key: String, value: Int): Boolean {
        return preferences.edit().putInt(key, value).commit()
    }

    fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }

    fun putLongSync(key: String, value: Long): Boolean {
        return preferences.edit().putLong(key, value).commit()
    }

    fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    fun putBooleanSync(key: String, value: Boolean): Boolean {
        return preferences.edit().putBoolean(key, value).commit()
    }

    fun putFloat(key: String, value: Float) {
        preferences.edit().putFloat(key, value).apply()
    }

    fun putFloatSync(key: String, value: Float): Boolean {
        return preferences.edit().putFloat(key, value).commit()
    }

    fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun putStringSync(key: String, value: String): Boolean {
        return preferences.edit().putString(key, value).commit()
    }

    fun putDouble(key: String, value: Double) {
        putString(key, value.toString())
    }

    fun putDoubleSync(key: String, value: Double): Boolean {
        return putStringSync(key, value.toString())
    }
}
