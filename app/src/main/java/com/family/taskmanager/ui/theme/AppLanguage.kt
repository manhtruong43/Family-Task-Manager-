package com.family.taskmanager.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import com.family.taskmanager.data.service.ServiceLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Quản lý ngôn ngữ trong app (đa ngôn ngữ - i18n).
 *
 * - Ngôn ngữ lưu trong SharedPreferences, dùng chung tên file/key với ServiceLocale
 *   để nội dung thông báo cũng theo đúng ngôn ngữ đã chọn.
 * - FamilyTaskManagerTheme lắng nghe [languageFlow]; khi đổi ngôn ngữ, toàn bộ
 *   stringResource() được vẽ lại NGAY LẬP TỨC, không cần khởi động lại app.
 */
object AppLanguage {
    const val VIETNAMESE = "vi"
    const val ENGLISH = "en"

    private var languageState: MutableStateFlow<String>? = null

    @Synchronized
    private fun state(context: Context): MutableStateFlow<String> {
        return languageState ?: MutableStateFlow(ServiceLocale.currentLanguage(context))
            .also { languageState = it }
    }

    fun languageFlow(context: Context): StateFlow<String> = state(context).asStateFlow()

    fun currentLanguage(context: Context): String = state(context).value

    fun setLanguage(context: Context, languageTag: String) {
        context.applicationContext
            .getSharedPreferences(ServiceLocale.LANGUAGE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ServiceLocale.KEY_LANGUAGE, languageTag)
            .apply()
        state(context).value = languageTag
    }

    /**
     * Tạo Context có Resources theo ngôn ngữ [languageTag].
     * Bọc bằng ContextWrapper (base = Activity) để Hilt (hiltViewModel) vẫn tìm thấy Activity.
     */
    fun localizedContext(base: Context, baseConfiguration: Configuration, languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale) // để SimpleDateFormat dùng Locale.getDefault() cũng đúng ngôn ngữ
        val config = Configuration(baseConfiguration)
        config.setLocale(locale)
        val localizedResources = base.createConfigurationContext(config).resources
        return LocalizedContextWrapper(base, localizedResources)
    }
}

class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}
