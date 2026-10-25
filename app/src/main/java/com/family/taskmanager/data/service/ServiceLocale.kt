package com.family.taskmanager.data.service

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Lấy Context theo ngôn ngữ người dùng đã chọn trong app (Cài đặt → Ngôn ngữ),
 * dùng cho các chuỗi sinh ra ở tầng service/repository (nội dung thông báo, quà mặc định...).
 *
 * Ngôn ngữ được lưu bởi AppLanguage (ui/theme/AppLanguage.kt - phần i18n),
 * nên tên file SharedPreferences và key bên dưới PHẢI trùng với AppLanguage.
 */
object ServiceLocale {
    const val LANGUAGE_PREFS = "family_task_settings"
    const val KEY_LANGUAGE = "app_language"
    private const val DEFAULT_LANGUAGE = "vi"

    fun currentLanguage(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(LANGUAGE_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    fun currentLocale(context: Context): Locale = Locale.forLanguageTag(currentLanguage(context))

    fun localizedContext(context: Context): Context {
        val appContext = context.applicationContext
        val config = Configuration(appContext.resources.configuration)
        config.setLocale(currentLocale(appContext))
        return appContext.createConfigurationContext(config)
    }
}
