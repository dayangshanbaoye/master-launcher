package com.rubyketang.launcher.ui.gesture

import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 05-product-spec.md §1.4 判定参数验收。GestureClassifier 不依赖 Compose 类型，
 * 距离类阈值（48dp/60dp）已由调用方换算成像素，这里只测比较逻辑本身。
 */
class GestureClassifierTest {

    private val threshold = 48f // 距离阈值，单位与测试里的 dx/dy 一致，数值即代表已换算后的 px

    // ---- 四方向基本判定：起点→终点向量，纯轴向 ----

    @Test
    fun `纯右滑 距离与耗时达标 判定为 right`() {
        assertEquals(
            SwipeDirection.RIGHT,
            GestureClassifier.classifySwipe(Vec(100f, 0f), elapsedMs = 100, distanceThresholdPx = threshold),
        )
    }

    @Test
    fun `纯左滑判定为 left`() {
        assertEquals(
            SwipeDirection.LEFT,
            GestureClassifier.classifySwipe(Vec(-100f, 0f), elapsedMs = 100, distanceThresholdPx = threshold),
        )
    }

    @Test
    fun `纯上滑判定为 up`() {
        assertEquals(
            SwipeDirection.UP,
            GestureClassifier.classifySwipe(Vec(0f, -100f), elapsedMs = 100, distanceThresholdPx = threshold),
        )
    }

    @Test
    fun `纯下滑判定为 down`() {
        assertEquals(
            SwipeDirection.DOWN,
            GestureClassifier.classifySwipe(Vec(0f, 100f), elapsedMs = 100, distanceThresholdPx = threshold),
        )
    }

    // ---- 距离阈值 48dp ----

    @Test
    fun `距离低于阈值 不响应`() {
        assertNull(
            GestureClassifier.classifySwipe(Vec(10f, 0f), elapsedMs = 100, distanceThresholdPx = threshold),
        )
    }

    @Test
    fun `距离恰好等于阈值 通过`() {
        assertEquals(
            SwipeDirection.RIGHT,
            GestureClassifier.classifySwipe(Vec(threshold, 0f), elapsedMs = 100, distanceThresholdPx = threshold),
        )
    }

    // ---- 时间上限 300ms ----

    @Test
    fun `耗时恰好 300ms 通过`() {
        assertEquals(
            SwipeDirection.RIGHT,
            GestureClassifier.classifySwipe(Vec(100f, 0f), elapsedMs = 300, distanceThresholdPx = threshold),
        )
    }

    @Test
    fun `耗时超过 300ms 不响应`() {
        assertNull(
            GestureClassifier.classifySwipe(Vec(100f, 0f), elapsedMs = 301, distanceThresholdPx = threshold),
        )
    }

    // ---- 主轴比 1.7：达不到就不判定，纯对角线（比例 1）必然落空 ----

    @Test
    fun `主轴比恰好 1_7 未超过 不判定`() {
        // absX/absY = 170/100 = 1.7，"达不到就不判定"按严格大于处理，等于不算数
        assertNull(
            GestureClassifier.classifySwipe(Vec(170f, 100f), elapsedMs = 100, distanceThresholdPx = threshold),
        )
    }

    @Test
    fun `主轴比略超 1_7 且角度达标 判定为 right`() {
        assertEquals(
            SwipeDirection.RIGHT,
            GestureClassifier.classifySwipe(Vec(171f, 100f), elapsedMs = 100, distanceThresholdPx = threshold),
        )
    }

    @Test
    fun `纯对角线 45 度 比例为 1 不判定`() {
        assertNull(
            GestureClassifier.classifySwipe(Vec(100f, 100f), elapsedMs = 100, distanceThresholdPx = threshold),
        )
        assertNull(
            GestureClassifier.classifySwipe(Vec(-100f, -100f), elapsedMs = 100, distanceThresholdPx = threshold),
        )
    }

    // ---- 左右容差 ±40° 与主轴比 1.7 的交界：主轴比先一步收紧到约 30.46°，
    //      名义上的 40° 容差在实践中不会成为真正的瓶颈 ----

    @Test
    fun `水平方向 35 度在名义 40 度容差内 但主轴比不足 1_7 仍不判定`() {
        val dx = 100f
        val dy = (dx * tan(Math.toRadians(35.0))).toFloat() // ≈70，ratio ≈1.43 < 1.7
        assertNull(GestureClassifier.classifySwipe(Vec(dx, dy), elapsedMs = 100, distanceThresholdPx = threshold))
    }

    // ---- 上下容差 ±30°：比主轴比隐含的 ~30.46° 更紧，是纵向真正生效的瓶颈 ----

    @Test
    fun `纵向 30_2 度 主轴比达标 但超出 30 度容差 仍不判定`() {
        val dy = 100f
        val dx = (dy * tan(Math.toRadians(30.2))).toFloat() // ratio ≈1.717 > 1.7，但角度超容差
        assertNull(GestureClassifier.classifySwipe(Vec(dx, dy), elapsedMs = 100, distanceThresholdPx = threshold))
    }

    @Test
    fun `纵向 25 度 两条件都达标 判定为 down`() {
        val dy = 100f
        val dx = (dy * tan(Math.toRadians(25.0))).toFloat()
        assertEquals(
            SwipeDirection.DOWN,
            GestureClassifier.classifySwipe(Vec(dx, dy), elapsedMs = 100, distanceThresholdPx = threshold),
        )
    }

    // ---- 双击窗口 250ms ----

    @Test
    fun `双击间隔恰好 250ms 判定为双击`() {
        assertTrue(GestureClassifier.isDoubleTap(250))
    }

    @Test
    fun `双击间隔 251ms 不判定为双击`() {
        assertFalse(GestureClassifier.isDoubleTap(251))
    }

    // ---- 长按触发 400ms ----

    @Test
    fun `按压 400ms 触发长按`() {
        assertTrue(GestureClassifier.isLongPress(400))
    }

    @Test
    fun `按压 399ms 不触发长按`() {
        assertFalse(GestureClassifier.isLongPress(399))
    }

    // ---- 双指判定：落点间距 ≥60dp 且落点时间差 <120ms ----

    @Test
    fun `双指落点间距达标 时间差 119ms 判定为双指手势`() {
        assertTrue(GestureClassifier.isTwoFingerGesture(pointerDistancePx = 60f, minDistancePx = 60f, timeDiffMs = 119))
    }

    @Test
    fun `双指落点间距不足 不判定为双指手势`() {
        assertFalse(GestureClassifier.isTwoFingerGesture(pointerDistancePx = 59f, minDistancePx = 60f, timeDiffMs = 50))
    }

    @Test
    fun `双指落点时间差恰好 120ms 不判定为双指手势`() {
        // 严格小于 120ms 才算，等于不算数
        assertFalse(GestureClassifier.isTwoFingerGesture(pointerDistancePx = 100f, minDistancePx = 60f, timeDiffMs = 120))
    }
}
