package com.family.taskmanager.ui.theme

import android.app.Activity
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
 * - Hộp thoại, menu xổ xuống, bảng trượt được Compose vẽ trong CỬA SỔ RIÊNG, lấy ngôn ngữ từ
 *   Resources của Activity chứ không từ CompositionLocalProvider của Theme -> phải đồng bộ cả
 *   Resources của Activity ([applyToActivity]), nếu không chúng vẫn hiện ngôn ngữ của máy.
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
     * Đặt ngôn ngữ [languageTag] cho Resources của Activity chứa [context].
     *
     * Dialog / Popup / DropdownMenu / ModalBottomSheet của Compose tạo cửa sổ riêng, và cửa sổ đó
     * đọc chuỗi từ Resources của Activity. Cập nhật tại chỗ nên đổi ngôn ngữ vẫn tức thì, không phải
     * tạo lại Activity. Gọi lại mỗi khi Activity được tạo lại (xoay màn hình...) vì khi đó Android
     * trả Resources về ngôn ngữ của máy - FamilyTaskManagerTheme đã gọi hàm này mỗi lần dựng lại.
     *
     * Resources.updateConfiguration đã bị Android đánh dấu deprecated (khuyên dùng
     * createConfigurationContext). Nhóm vẫn dùng để đổi ngôn ngữ của Activity đang chạy mà không phải
     * tạo lại nó; đã kiểm bằng test Robolectric (SDK 28), cần thử tay thêm trên máy thật.
     * Cách chính thống thay thế: AppCompatDelegate.setApplicationLocales (tạo lại Activity khi đổi).
     */
    @Suppress("DEPRECATION")
    fun applyToActivity(context: Context, languageTag: String) {
        val resources = context.findActivity()?.resources ?: return
        val locale = Locale.forLanguageTag(languageTag)
        val current = resources.configuration
        if (current.locales[0] == locale) return
        val config = Configuration(current)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
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

/** Tìm Activity phía sau một Context (có thể đã bị bọc nhiều lớp ContextWrapper). */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
