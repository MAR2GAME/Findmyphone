package com.findmyphone.gpstracker.bean


import java.util.Locale

sealed class SetLanguageEvent {
    data class SaveLanguage(val locale: Locale) : SetLanguageEvent()
}