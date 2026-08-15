package com.rubyketang.launcher.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 长按触发 400ms（05-product-spec.md §1.4），只挂在 Canvas 的空白区域；
 * 达到阈值后显示手势速查表，松手消失。
 *
 * "松手消失"和"每条可就地改绑"字面上是矛盾的——手指一抬浮层就没了，不可能先抬手再点。
 * 这里按电源键长按菜单那种经典交互实现：按住不放触发浮层，手指拖到目标行上再松手才算选中；
 * 命中判定交给调用方（[onReleaseAt] 给的是松手时的原始触点位置，调用方自己转换坐标系、
 * 跟每一行通过 `onGloballyPositioned` 记录的位置做命中测试），这个函数只管手势识别本身。
 */
fun Modifier.quickReferenceLongPress(
    longPressMs: Long = GestureParams.LONG_PRESS_MS,
    onVisibilityChanged: (Boolean) -> Unit,
    onReleaseAt: (Offset) -> Unit,
): Modifier = pointerInput(longPressMs) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val primaryId = down.id
        var lastPosition = down.position

        val timedOut = withTimeoutOrNull(longPressMs) {
            while (true) {
                val event = awaitPointerEvent()
                val primary = event.changes.firstOrNull { it.id == primaryId } ?: return@withTimeoutOrNull
                if (!primary.pressed) return@withTimeoutOrNull // 没到阈值就松手：不是这个手势，什么都不做
                lastPosition = primary.position
            }
            @Suppress("UNREACHABLE_CODE") Unit
        } == null

        if (!timedOut) return@awaitEachGesture

        onVisibilityChanged(true)
        while (true) {
            val event = awaitPointerEvent()
            val primary = event.changes.firstOrNull { it.id == primaryId }
            if (primary == null || !primary.pressed) {
                primary?.let { lastPosition = it.position }
                break
            }
            lastPosition = primary.position
            primary.consume()
        }
        onVisibilityChanged(false)
        onReleaseAt(lastPosition)
    }
}
