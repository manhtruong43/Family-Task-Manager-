package com.family.taskmanager

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.family.taskmanager.ui.theme.AppLanguage
import com.family.taskmanager.ui.theme.FamilyTaskManagerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Hộp thoại, menu xổ xuống, bảng trượt phải theo ngôn ngữ chọn trong app, không theo ngôn ngữ máy.
 * Máy giả lập để tiếng Việt (qualifiers = "vi"). Compose vẽ 3 loại này trong CỬA SỔ RIÊNG, đọc
 * chuỗi từ Resources của Activity - trước khi sửa, chọn English thì chúng vẫn hiện tiếng Việt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "vi-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class AppLanguageDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private enum class Window { DIALOG, MENU, SHEET }
    private val shown = mutableStateOf<Window?>(null)

    /** Chuỗi (vi, en) được hiện trong từng loại cửa sổ. */
    private val texts = mapOf(
        Window.DIALOG to ("Xác nhận đổi quà" to "Confirm redemption"),
        Window.MENU to ("Cả nhà" to "Everyone"),
        Window.SHEET to ("Tạo lịch trình cố định" to "New fixed schedule")
    )

    // Gọi lại sau recreate(): Activity mới chưa có nội dung.
    private fun setUi() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                FamilyTaskManagerTheme(dynamicColor = false) {
                    Text(stringResource(R.string.rw_dialog_create_title)) // màn chính
                    when (shown.value) {
                        Window.DIALOG -> AlertDialog(
                            onDismissRequest = {}, confirmButton = {},
                            title = { Text(stringResource(R.string.rw_redeem_title)) }
                        )
                        Window.MENU -> Box {
                            DropdownMenu(expanded = true, onDismissRequest = {}) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.rec_everyone)) }, onClick = {})
                            }
                        }
                        Window.SHEET -> ModalBottomSheet(onDismissRequest = {}) {
                            Text(stringResource(R.string.rec_dialog_create))
                        }
                        null -> Unit
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Before
    fun startInVietnamese() {
        AppLanguage.setLanguage(compose.activity, AppLanguage.VIETNAMESE)
        setUi()
    }

    /** AppLanguage giữ trạng thái trong object tĩnh -> trả về tiếng Việt để không ảnh hưởng test khác. */
    @After
    fun backToVietnamese() {
        AppLanguage.setLanguage(compose.activity, AppLanguage.VIETNAMESE)
    }

    private fun switchTo(language: String) {
        compose.runOnIdle { AppLanguage.setLanguage(compose.activity, language) }
        compose.waitForIdle()
    }

    /** Mở lần lượt từng loại cửa sổ, kiểm tra đúng ngôn ngữ rồi đóng lại. */
    private fun assertAllWindows(english: Boolean) {
        compose.onNodeWithText(if (english) "Add reward" else "Thêm phần thưởng").assertExists()
        for ((window, pair) in texts) {
            val (vi, en) = pair
            compose.runOnIdle { shown.value = window }
            compose.waitForIdle()
            compose.onNodeWithText(if (english) en else vi).assertExists()
            compose.onNodeWithText(if (english) vi else en).assertDoesNotExist()
            compose.runOnIdle { shown.value = null }
            compose.waitForIdle()
        }
    }

    @Test
    fun windowsFollowEnglish_whenPhoneIsVietnamese() {
        switchTo(AppLanguage.ENGLISH)
        assertAllWindows(english = true)
    }

    @Test
    fun windowsFollowEachSwitch_backAndForth() {
        assertAllWindows(english = false)
        switchTo(AppLanguage.ENGLISH)
        assertAllWindows(english = true)
        switchTo(AppLanguage.VIETNAMESE)
        assertAllWindows(english = false)
    }

    @Test
    fun windowsStayEnglish_afterActivityRecreated() {
        switchTo(AppLanguage.ENGLISH)
        compose.activityRule.scenario.recreate() // như xoay màn hình: Android trả Resources về ngôn ngữ máy
        setUi()
        assertAllWindows(english = true)
    }
}
