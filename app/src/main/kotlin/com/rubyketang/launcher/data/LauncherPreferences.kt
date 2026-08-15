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

    companion object {
        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.30f
        private const val FONT_SCALE = "font_scale"
        private const val TWO_FINGER_DOWN_ACTION = "two_finger_down_action"
    }
}
