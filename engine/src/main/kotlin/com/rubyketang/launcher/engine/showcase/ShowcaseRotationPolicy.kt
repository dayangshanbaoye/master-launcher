package com.rubyketang.launcher.engine.showcase

/** 05-product-spec.md §2.5：海报模式切换时机，用户在"相册"设置组里选。 */
enum class ShowcaseSwitchTiming { EVERY_UNLOCK, DAILY, MANUAL }

/** [ShowcaseRotationPolicy.snapshot] 的返回值，进 LauncherPreferences 持久化（本机视觉状态，不进跨设备快照）。 */
data class ShowcaseRotationSnapshot(val index: Int, val lastSwitchedDay: Long)

/**
 * 展示区海报模式"当前显示第几张"的纯逻辑，不碰 Bitmap/Uri/Context。
 * 相册墙模式不消费这个策略——"墙本身不翻页、不动，全部照片一次性呈现"（§2.5），
 * 只有海报模式的单图轮换需要它；全屏查看器内的手动翻页也不经过这里，
 * 那是查看器自己的 pager 状态，到头不循环（§2.5 全屏查看器"到头不循环，末端阻尼回弹"，
 * 跟这里"轮到下一张"的自动播放语义不是一回事）。
 *
 * 首次调用只记录基准日、显示第一张，不算一次"切换"——不然冷启动第一眼就跳过第一张图。
 */
class ShowcaseRotationPolicy {
    private var index = 0
    private var lastSwitchedDay = NEVER

    /** 每次解锁调用一次；photoCount 随时可能变（文件夹改了），每次都纠正越界。 */
    fun onUnlock(photoCount: Int, timing: ShowcaseSwitchTiming, now: Long): Int {
        if (photoCount <= 0) {
            index = 0
            return 0
        }
        clampToCount(photoCount)
        val today = now / DAY_MILLIS
        if (lastSwitchedDay == NEVER) {
            lastSwitchedDay = today
            return index
        }
        val shouldAdvance = when (timing) {
            ShowcaseSwitchTiming.EVERY_UNLOCK -> true
            ShowcaseSwitchTiming.DAILY -> today != lastSwitchedDay
            ShowcaseSwitchTiming.MANUAL -> false
        }
        if (shouldAdvance) {
            index = (index + 1) % photoCount
            lastSwitchedDay = today
        }
        return index
    }

    /** 手动切换用（若产品后续要在海报模式加左右滑，目前 UI 未接，先备好接口）。 */
    fun next(photoCount: Int): Int {
        if (photoCount <= 0) return 0
        clampToCount(photoCount)
        index = (index + 1) % photoCount
        return index
    }

    fun previous(photoCount: Int): Int {
        if (photoCount <= 0) return 0
        clampToCount(photoCount)
        index = (index - 1 + photoCount) % photoCount
        return index
    }

    fun currentIndex(photoCount: Int): Int {
        clampToCount(photoCount)
        return index
    }

    private fun clampToCount(photoCount: Int) {
        if (photoCount <= 0) {
            index = 0
        } else if (index >= photoCount) {
            index %= photoCount
        }
    }

    fun snapshot(): ShowcaseRotationSnapshot = ShowcaseRotationSnapshot(index, lastSwitchedDay)

    fun restore(saved: ShowcaseRotationSnapshot) {
        index = saved.index
        lastSwitchedDay = saved.lastSwitchedDay
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val NEVER = -1L
    }
}
