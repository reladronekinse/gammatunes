package com.gammatunes.app.ui.i18n

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocaleRepository {
    private const val PREFS = "ytm_locale"
    private const val KEY_LANG = "language"

    private val _language = MutableStateFlow(AppLanguage.ENGLISH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_LANG, AppLanguage.ENGLISH.code)
        _language.value = AppLanguage.fromCode(code)
    }

    fun setLanguage(context: Context, lang: AppLanguage) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, lang.code)
            .apply()
        _language.value = lang
    }
}
