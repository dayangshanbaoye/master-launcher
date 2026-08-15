package com.rubyketang.launcher.ui.gesture

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import com.rubyketang.launcher.ui.theme.Dimens
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 手势识别层：只识别、只回调 gestureId，零业务逻辑。参数取自 05-product-spec.md §1.4。
 *
 * 单指方向判定用 Google 官方的 `detectDragGestures`（正确处理触摸容差前不消费事件，
 * 不会打断子级 combinedClickable 的零延迟点击），只把"分类成哪个方向"这一步换成
 * 可单测的 [GestureClassifier]。双指下滑是全新能力，Compose 没有现成 API，单独写了
 * [detectTwoFingerDownSwipe]——它在真正确认是双指手势之前完全不消费任何事件，
 * 所以不会干扰同时跑在这个 Modifier 上的单指检测。
 *
 * - 竖直上滑 → "up"
 * - 竖直下滑 → "down"
 * - 水平左滑 → "left"
 * - 水平右滑 → "right"
 * - 双指下滑 → "two_finger_down"
 *
 * 双击（回首页）和长按（手势速查表）分别用 `detectTapGestures`/[quickReferenceLongPress]
 * 实现，不在这个函数里——它们各自的判定天然依赖 Compose 自带的点按协作协议，混进同一个
 * 手写状态机里风险更高，见 05-product-spec.md §1.4「双击的实现纪律」。
 */
@Composable
fun Modifier.launcherGestures(
    onGesture: (String) -> Unit,
): Modifier {
    val distanceThresholdPx = with(LocalDensity.current) { Dimens.GestureThreshold.toPx() }
    val twoFingerMinDistancePx = with(LocalDensity.current) { Dimens.TwoFingerMinDistance.toPx() }
    return this
        .pointerInput(distanceThresholdPx) {
            var startTime = 0L
            var acc = Offset.Zero
            detectDragGestures(
                onDragStart = {
                    startTime = SystemClock.uptimeMillis()
                    acc = Offset.Zero
                },
                onDrag = { change, dragAmount ->
                    acc += dragAmount
                    change.consume()
                },
                onDragEnd = {
                    val elapsed = SystemClock.uptimeMillis() - startTime
                    GestureClassifier.classifySwipe(Vec(acc.x, acc.y), elapsed, distanceThresholdPx)
                        ?.let { onGesture(it.name.lowercase()) }
                },
            )
        }
        .pointerInput(twoFingerMinDistancePx, distanceThresholdPx) {
            detectTwoFingerDownSwipe(twoFingerMinDistancePx, distanceThresholdPx, onGesture)
        }
}

/**
 * 双指下滑判定（05-product-spec.md §1.4）：两指落点间距 ≥60dp 且落点时间差 <120ms 才算数。
 * 在确认之前完全不 consume 任何事件——不是双指手势就悄悄放弃，交回给并发跑着的单指检测器。
 */
private suspend fun PointerInputScope.detectTwoFingerDownSwipe(
    minDistancePx: Float,
    distanceThresholdPx: Float,
    onGesture: (String) -> Unit,
) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        val firstId = first.id
        val firstDownTime = SystemClock.uptimeMillis()

        var secondId: PointerId? = null
        withTimeoutOrNull(GestureParams.TWO_FINGER_MAX_TIME_DIFF_MS) {
            while (secondId == null) {
                val event = awaitPointerEvent()
                val second = event.changes.firstOrNull { it.id != firstId && it.pressed && !it.previousPressed }
                if (second != null) {
                    val elapsed = SystemClock.uptimeMillis() - firstDownTime
                    val dist = (second.position - first.position).getDistance()
                    if (GestureClassifier.isTwoFingerGesture(dist, minDistancePx, elapsed)) {
                        secondId = second.id
                    }
                }
                if (event.changes.none { it.pressed }) return@withTimeoutOrNull
            }
        }
        val confirmedSecondId = secondId ?: return@awaitEachGesture

        // 已确认双指起手：独占追踪直到两指都松开，按第一指的位移分类方向（只认下滑）。
        var acc = Offset.Zero
        val startTime = SystemClock.uptimeMillis()
        while (true) {
            val event = awaitPointerEvent()
            event.changes.firstOrNull { it.id == firstId }?.let { primary ->
                if (primary.pressed) {
                    acc += primary.positionChange()
                }
                primary.consume()
            }
            event.changes.firstOrNull { it.id == confirmedSecondId }?.consume()
            if (event.changes.none { it.pressed }) break
        }
        val elapsed = SystemClock.uptimeMillis() - startTime
        if (GestureClassifier.classifySwipe(Vec(acc.x, acc.y), elapsed, distanceThresholdPx) == SwipeDirection.DOWN) {
            onGesture("two_finger_down")
        }
    }
}
