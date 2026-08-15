package com.rubyketang.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 05-product-spec.md §1.2：双指下滑默认绑通知栏，可在速查表里就地改绑锁屏，或留空。 */
enum class TwoFingerDownAction {
    NOTIFICATIONS, LOCK_SCREEN, NONE;

    /** 就地改绑循环顺序：通知栏 → 锁屏 → 留空 → 通知栏…… */
    fun next(): TwoFingerDownAction = when (this) {
        NOTIFICATIONS -> LOCK_SCREEN
        LOCK_SCREEN -> NONE
        NONE -> NOTIFICATIONS
    }
}

/** 05-product-spec.md §2.3：惯用手决定固定簇/推荐簇的左右分布，推荐簇落在惯用手一侧下角。 */
enum class Handedness { RIGHT, LEFT }

/** 仅保存本机视觉偏好，不进入跨设备索引快照。 */
class LauncherPreferences(context: Context) {
    private val storage = context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
    private val _fontScale = MutableStateFlow(storage.getFloat(FONT_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE))
    val fontScale: StateFlow<Float> = _fontScale

    private val _twoFingerDownAction = MutableStateFlow(
        storage.getString(TWO_FINGER_DOWN_ACTION, null)
            ?.let { runCatching { TwoFingerDownAction.valueOf(it) }.getOrNull() }
            ?: TwoFingerDownAction.NOTIFICATIONS,
    )
    val twoFingerDownAction: StateFlow<TwoFingerDownAction> = _twoFingerDownAction

    private val _handedness = MutableStateFlow(
        storage.getString(HANDEDNESS, null)
            ?.let { runCatching { Handedness.valueOf(it) }.getOrNull() }
            ?: Handedness.RIGHT,
    )
    val handedness: StateFlow<Handedness> = _handedness

    // §2.2 时钟区四元素：时间恒显示，星期/日期/农历可各自勾选。星期+日期默认开，跟现状一致；
    // 农历是全新功能，默认关，用户自己开。
    private val _showWeekday = MutableStateFlow(storage.getBoolean(SHOW_WEEKDAY, true))
    val showWeekday: StateFlow<Boolean> = _showWeekday
    private val _showDate = MutableStateFlow(storage.getBoolean(SHOW_DATE, true))
    val showDate: StateFlow<Boolean> = _showDate
    private val _showLunar = MutableStateFlow(storage.getBoolean(SHOW_LUNAR, false))
    val showLunar: StateFlow<Boolean> = _showLunar

    fun setFontScale(scale: Float) {
        val clean = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        _fontScale.value = clean
        storage.edit().putFloat(FONT_SCALE, clean).apply()
    }

    fun cycleTwoFingerDownAction() {
        val next = _twoFingerDownAction.value.next()
        _twoFingerDownAction.value = next
        storage.edit().putString(TWO_FINGER_DOWN_ACTION, next.name).apply()
    }

    fun setHandedness(value: Handedness) {
        _handedness.value = value
        storage.edit().putString(HANDEDNESS, value.name).apply()
    }

    fun setShowWeekday(value: Boolean) = setClockToggle(_showWeekday, SHOW_WEEKDAY, value)
    fun setShowDate(value: Boolean) = setClockToggle(_showDate, SHOW_DATE, value)
    fun setShowLunar(value: Boolean) = setClockToggle(_showLunar, SHOW_LUNAR, value)

    private fun setClockToggle(flow: MutableStateFlow<Boolean>, key: String, value: Boolean) {
        flow.value = value
        storage.edit().putBoolean(key, value).apply()
    }

    companion object {
        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.30f
        private const val FONT_SCALE = "font_scale"
        private const val TWO_FINGER_DOWN_ACTION = "two_finger_down_action"
        private const val HANDEDNESS = "handedness"
        private const val SHOW_WEEKDAY = "show_weekday"
        private const val SHOW_DATE = "show_date"
        private const val SHOW_LUNAR = "show_lunar"
    }
}
