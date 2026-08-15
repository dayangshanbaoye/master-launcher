package com.rubyketang.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 仅保存本机视觉偏好，不进入跨设备索引快照。 */
class LauncherPreferences(context: Context) {
    private val storage = context.getSharedPreferences("launcher_preferences", Context.MODE_PRIVATE)
    private val _fontScale = MutableStateFlow(storage.getFloat(FONT_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE))
    val fontScale: StateFlow<Float> = _fontScale

    fun setFontScale(scale: Float) {
        val clean = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        _fontScale.value = clean
        storage.edit().putFloat(FONT_SCALE, clean).apply()
    }

    companion object {
        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.30f
        private const val FONT_SCALE = "font_scale"
    }
}
