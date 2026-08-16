package com.rubyketang.launcher.data

import android.content.Context
import com.rubyketang.launcher.engine.showcase.ShowcaseRotationSnapshot
import com.rubyketang.launcher.engine.showcase.ShowcaseSwitchTiming
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

/** 05-product-spec.md §2.5：海报单图 / 相册墙宫格。选相册墙时 app 槽自动减为 4（仅保留固定簇）。 */
enum class ShowcaseMode { POSTER, WALL }

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

    // §2.5 展示区：模式（海报/相册墙）、切换时机、图片来源文件夹（SAF 树 URI 字符串）。
    // 跟 handedness 一样是本机视觉偏好，不进跨设备索引快照——换一台设备没有理由继承这台机器的相册文件夹。
    private val _showcaseMode = MutableStateFlow(
        storage.getString(SHOWCASE_MODE, null)
            ?.let { runCatching { ShowcaseMode.valueOf(it) }.getOrNull() }
            ?: ShowcaseMode.POSTER,
    )
    val showcaseMode: StateFlow<ShowcaseMode> = _showcaseMode
    private val _showcaseSwitchTiming = MutableStateFlow(
        storage.getString(SHOWCASE_SWITCH_TIMING, null)
            ?.let { runCatching { ShowcaseSwitchTiming.valueOf(it) }.getOrNull() }
            ?: ShowcaseSwitchTiming.EVERY_UNLOCK,
    )
    val showcaseSwitchTiming: StateFlow<ShowcaseSwitchTiming> = _showcaseSwitchTiming
    private val _showcaseFolderUri = MutableStateFlow(storage.getString(SHOWCASE_FOLDER_URI, null))
    val showcaseFolderUri: StateFlow<String?> = _showcaseFolderUri
    // 海报模式"当前第几张"的轮换状态（ShowcaseRotationPolicy 的 snapshot）——冷启动要能接着上次的
    // 位置继续轮换，不然每次重启都从第一张开始，"每天一次"这种慢速切换的效果就荡然无存。
    private var showcaseRotationIndex = storage.getInt(SHOWCASE_ROTATION_INDEX, 0)
    private var showcaseRotationLastDay = storage.getLong(SHOWCASE_ROTATION_LAST_DAY, -1L)

    // §4.2 冷启动引导只做一次；§4.6 默认桌面重置横幅状态。
    private val _onboardingDone = MutableStateFlow(storage.getBoolean(ONBOARDING_DONE, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone
    private val _wasDefaultLauncherConfirmed = MutableStateFlow(storage.getBoolean(WAS_DEFAULT_LAUNCHER_CONFIRMED, false))
    val wasDefaultLauncherConfirmed: StateFlow<Boolean> = _wasDefaultLauncherConfirmed
    private val _defaultLauncherBannerDismissedAt = MutableStateFlow(storage.getLong(DEFAULT_LAUNCHER_BANNER_DISMISSED_AT, 0L))
    val defaultLauncherBannerDismissedAt: StateFlow<Long> = _defaultLauncherBannerDismissedAt

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

    fun setOnboardingDone(value: Boolean) {
        _onboardingDone.value = value
        storage.edit().putBoolean(ONBOARDING_DONE, value).apply()
    }

    fun setWasDefaultLauncherConfirmed(value: Boolean) {
        _wasDefaultLauncherConfirmed.value = value
        storage.edit().putBoolean(WAS_DEFAULT_LAUNCHER_CONFIRMED, value).apply()
    }

    fun setDefaultLauncherBannerDismissedAt(value: Long) {
        _defaultLauncherBannerDismissedAt.value = value
        storage.edit().putLong(DEFAULT_LAUNCHER_BANNER_DISMISSED_AT, value).apply()
    }

    fun setShowcaseMode(value: ShowcaseMode) {
        _showcaseMode.value = value
        storage.edit().putString(SHOWCASE_MODE, value.name).apply()
    }

    /** 就地循环用，跟速查表里双指下滑改绑同一套交互——设置页没有下拉选择器这种控件。 */
    fun cycleShowcaseSwitchTiming() {
        val next = when (_showcaseSwitchTiming.value) {
            ShowcaseSwitchTiming.EVERY_UNLOCK -> ShowcaseSwitchTiming.DAILY
            ShowcaseSwitchTiming.DAILY -> ShowcaseSwitchTiming.MANUAL
            ShowcaseSwitchTiming.MANUAL -> ShowcaseSwitchTiming.EVERY_UNLOCK
        }
        _showcaseSwitchTiming.value = next
        storage.edit().putString(SHOWCASE_SWITCH_TIMING, next.name).apply()
    }

    /** 选完文件夹后调用；旧文件夹的缩略图磁盘缓存由调用方（LauncherState）负责清掉。 */
    fun setShowcaseFolderUri(uri: String?) {
        _showcaseFolderUri.value = uri
        storage.edit().putString(SHOWCASE_FOLDER_URI, uri).apply()
    }

    fun loadShowcaseRotation(): ShowcaseRotationSnapshot =
        ShowcaseRotationSnapshot(showcaseRotationIndex, showcaseRotationLastDay)

    fun saveShowcaseRotation(snapshot: ShowcaseRotationSnapshot) {
        showcaseRotationIndex = snapshot.index
        showcaseRotationLastDay = snapshot.lastSwitchedDay
        storage.edit()
            .putInt(SHOWCASE_ROTATION_INDEX, snapshot.index)
            .putLong(SHOWCASE_ROTATION_LAST_DAY, snapshot.lastSwitchedDay)
            .apply()
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
        private const val ONBOARDING_DONE = "onboarding_done"
        private const val WAS_DEFAULT_LAUNCHER_CONFIRMED = "was_default_launcher_confirmed"
        private const val DEFAULT_LAUNCHER_BANNER_DISMISSED_AT = "default_launcher_banner_dismissed_at"
        private const val SHOWCASE_MODE = "showcase_mode"
        private const val SHOWCASE_SWITCH_TIMING = "showcase_switch_timing"
        private const val SHOWCASE_FOLDER_URI = "showcase_folder_uri"
        private const val SHOWCASE_ROTATION_INDEX = "showcase_rotation_index"
        private const val SHOWCASE_ROTATION_LAST_DAY = "showcase_rotation_last_day"
    }
}
