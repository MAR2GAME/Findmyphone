package com.findmyphone.gpstracker.utils

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceWriterTest {

    @Test
    fun putStringUsesApplyByDefault() {
        val preferences = FakeSharedPreferences()
        val writer = PreferenceWriter(preferences)

        writer.putString("token", "abc")

        assertEquals("abc", preferences.values["token"])
        assertEquals(1, preferences.editor.applyCalls)
        assertEquals(0, preferences.editor.commitCalls)
    }

    @Test
    fun putStringSyncUsesCommit() {
        val preferences = FakeSharedPreferences()
        val writer = PreferenceWriter(preferences)

        writer.putStringSync("token", "abc")

        assertEquals("abc", preferences.values["token"])
        assertEquals(0, preferences.editor.applyCalls)
        assertEquals(1, preferences.editor.commitCalls)
    }

    private class FakeSharedPreferences : SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        val editor = FakeEditor(values)

        override fun edit(): SharedPreferences.Editor = editor
        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (values[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private class FakeEditor(
        private val values: MutableMap<String, Any?>
    ) : SharedPreferences.Editor {
        var applyCalls = 0
        var commitCalls = 0
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = put(key, values)
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            key?.let { removals.add(it) }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            commitCalls++
            flush()
            return true
        }

        override fun apply() {
            applyCalls++
            flush()
        }

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            key?.let { pending[it] = value }
            return this
        }

        private fun flush() {
            if (clearRequested) {
                values.clear()
                clearRequested = false
            }
            removals.forEach(values::remove)
            removals.clear()
            values.putAll(pending)
            pending.clear()
        }
    }
}
