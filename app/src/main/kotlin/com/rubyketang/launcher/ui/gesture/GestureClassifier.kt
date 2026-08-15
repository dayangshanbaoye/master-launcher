package com.rubyketang.launcher.ui.gesture

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 05-product-spec.md §1.4 判定参数，纯数值常量，不含 dp/px 换算（那是 Compose 层的事）。
 */
object GestureParams {
    const val MAX_DURATION_MS = 300L
    const val DOUBLE_TAP_WINDOW_MS = 250L
    const val LONG_PRESS_MS = 400L
    const val TWO_FINGER_MAX_TIME_DIFF_MS = 120L
    const val HORIZONTAL_ANGLE_TOLERANCE_DEG = 40.0
    const val VERTICAL_ANGLE_TOLERANCE_DEG = 30.0
    const val PRIMARY_AXIS_RATIO = 1.7f
}

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/** 起点→终点位移向量。单位与调用方传入的阈值一致（GestureLayer 里已经是 px）。 */
data class Vec(val dx: Float, val dy: Float) {
    val distance: Float get() = hypot(dx, dy)
}

/**
 * 纯逻辑手势判定层，不依赖任何 Compose 类型，可用纯 JVM 单测覆盖。
 * 对应 05-product-spec.md §1.4：方向判定只看起点→终点向量，不看中间轨迹；
 * 超出容差直接返回 null（不响应），不做最近邻猜测。
 *
 * 主轴比 >1.7 与左右/上下角度容差是同时生效的两条约束，不是二选一：
 * 换算成角度，主轴比 1.7 约等于 30.46°。这比左右容差 ±40° 更严格，
 * 也就是说左右方向真正生效的瓶颈实际上是主轴比（约 30.5°），±40° 这个数字
 * 在当前两条约束同时存在的前提下不会被触达；上下容差 ±30° 则比主轴比隐含的
 * 30.46° 更紧一点，是纵向真正生效的那条。这个交界行为在 GestureClassifierTest
 * 里有专门的边界用例钉住，改动前先看那几条测试。
 */
object GestureClassifier {

    fun classifySwipe(
        vector: Vec,
        elapsedMs: Long,
        distanceThresholdPx: Float,
    ): SwipeDirection? {
        if (elapsedMs > GestureParams.MAX_DURATION_MS) return null
        if (vector.distance < distanceThresholdPx) return null

        val absX = abs(vector.dx)
        val absY = abs(vector.dy)
        return if (absX >= absY) {
            if (!passesPrimaryAxisRatio(absX, absY)) return null
            if (angleFromAxisDeg(absX, absY) > GestureParams.HORIZONTAL_ANGLE_TOLERANCE_DEG) return null
            if (vector.dx < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT
        } else {
            if (!passesPrimaryAxisRatio(absY, absX)) return null
            if (angleFromAxisDeg(absY, absX) > GestureParams.VERTICAL_ANGLE_TOLERANCE_DEG) return null
            if (vector.dy < 0) SwipeDirection.UP else SwipeDirection.DOWN
        }
    }

    /** 双击窗口 250ms：两次点击间隔在窗口内才算双击。 */
    fun isDoubleTap(intervalMs: Long): Boolean = intervalMs in 0..GestureParams.DOUBLE_TAP_WINDOW_MS

    /** 长按触发 400ms。 */
    fun isLongPress(pressDurationMs: Long): Boolean = pressDurationMs >= GestureParams.LONG_PRESS_MS

    /** 双指判定：两指落点间距 ≥[minDistancePx] 且落点时间差 <120ms，才算一次双指手势起手。 */
    fun isTwoFingerGesture(pointerDistancePx: Float, minDistancePx: Float, timeDiffMs: Long): Boolean =
        pointerDistancePx >= minDistancePx && timeDiffMs < GestureParams.TWO_FINGER_MAX_TIME_DIFF_MS

    /** 主方向位移 / 次方向位移 > 1.7，达不到就不判定（等于不算数，必须严格超过）。 */
    private fun passesPrimaryAxisRatio(primary: Float, secondary: Float): Boolean =
        secondary == 0f || primary / secondary > GestureParams.PRIMARY_AXIS_RATIO

    /** 向量偏离 [primary] 所在轴的角度（度）。 */
    private fun angleFromAxisDeg(primary: Float, secondary: Float): Double =
        Math.toDegrees(atan2(secondary.toDouble(), primary.toDouble()))
}
