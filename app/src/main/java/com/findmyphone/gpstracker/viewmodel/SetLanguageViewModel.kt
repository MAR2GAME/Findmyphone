package com.findmyphone.gpstracker.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.findmyphone.gpstracker.base.viewmodel.BaseViewModel
import com.findmyphone.gpstracker.bean.SetLanguageEvent
import com.findmyphone.gpstracker.utils.LocaleHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Locale

class SetLanguageViewModel(application: Application) : BaseViewModel(application) {

    val languageMap: Map<String, Locale> = mapOf(
        "English" to Locale.ENGLISH,
        "français" to Locale.FRENCH,
        "Deutsch" to Locale.forLanguageTag("de"),
        "português" to Locale.forLanguageTag("pt"),
        "español" to Locale.forLanguageTag("es"),
        "Indonesia" to Locale.forLanguageTag("id"),
        "italiano" to Locale.ITALIAN,
        "日本語" to Locale.JAPANESE,
        "한국어" to Locale.KOREAN,
        "中文 (简体)" to Locale.forLanguageTag("zh-CN"),
        "中文 (繁体)" to Locale.forLanguageTag("zh-TW"),
    )

    val languageKeys: MutableList<String> = languageMap.keys.toMutableList()

    private val _selectedLanguageKey = MutableStateFlow<String?>(null)
    val selectedLanguageKey: StateFlow<String?> = _selectedLanguageKey

    private val _events = MutableSharedFlow<SetLanguageEvent>()
    val events = _events.asSharedFlow()

    init {
        // 使用 Application Context 获取默认语言
        _selectedLanguageKey.value = LocaleHelper.getDefaultLanguageKey(getApplication(), languageMap)
    }

    fun selectLanguage(key: String) {
        _selectedLanguageKey.value = key
    }

    fun saveLanguage() {
        val locale = languageMap[_selectedLanguageKey.value] ?: return
        viewModelScope.launch {
            _events.emit(SetLanguageEvent.SaveLanguage(locale))
        }
    }
}