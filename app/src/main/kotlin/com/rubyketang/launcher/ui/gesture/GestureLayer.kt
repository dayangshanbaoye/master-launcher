package com.rubyketang.launcher.ui.gesture

import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import com.rubyketang.launcher.ui.theme.Dimens
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * P0-8 手势识别层：只识别、只回调 gestureId，零业务逻辑。
 * 阈值 48dp / 上限 300ms，起点落在 Canvas 空白区（由调用方保证挂载在背景上）。
 *
 * - 竖直上滑 → "up"（Search）
 * - 竖直下滑 → "down"（Browse）
 * - 四角起手的对角滑（朝屏内）→ "corner_tl/tr/bl/br"（4 个自定义槽）
 */
@Composable
fun Modifier.launcherGestures(
    onGesture: (String) -> Unit,
): Modifier {
    val thresholdPx = with(LocalDensity.current) { Dimens.GestureThreshold.toPx() }
    val cornerZonePx = with(LocalDensity.current) { Dimens.CornerGestureZone.toPx() }
    return pointerInput(thresholdPx, cornerZonePx) {
        var start = Offset.Zero
        var startTime = 0L
        var acc = Offset.Zero
        detectDragGestures(
            onDragStart = { offset ->
                start = offset
                startTime = SystemClock.uptimeMillis()
                acc = Offset.Zero
            },
            onDrag = { change, dragAmount ->
                acc += dragAmount
                change.consume()
            },
            onDragEnd = {
                val elapsed = SystemClock.uptimeMillis() - startTime
                if (elapsed > MAX_DURATION_MS || acc.getDistance() < thresholdPx) return@detectDragGestures
                val corner = size.cornerOf(start, cornerZonePx)
                val gesture = when {
                    corner != null && movesInward(corner, acc, thresholdPx) -> corner
                    kotlin.math.abs(acc.y) > kotlin.math.abs(acc.x) -> if (acc.y < 0) "up" else "down"
                    else -> if (acc.x < 0) "left" else "right"
                }
                gesture?.let(onGesture)
            },
        )
    }
}

private const val MAX_DURATION_MS = 450L

/** 起点落在四个角落区域（30% × 30%）则视为角滑起手。 */
private fun androidx.compose.ui.unit.IntSize.cornerOf(start: Offset, zone: Float): String? {
    val cx = min(width * 0.18f, zone)
    val cy = min(height * 0.18f, zone)
    val left = start.x < cx
    val right = start.x > width - cx
    val top = start.y < cy
    val bottom = start.y > height - cy
    return when {
        top && left -> "corner_tl"
        top && right -> "corner_tr"
        bottom && left -> "corner_bl"
        bottom && right -> "corner_br"
        else -> null
    }
}

/** 角滑必须朝屏幕内侧（如左上角落 → 向右下）。 */
private fun movesInward(corner: String, acc: Offset, threshold: Float): Boolean {
    // 角滑必须是足够长且接近 45° 的对角线：避免右上角的普通上滑被误判。
    if (min(abs(acc.x), abs(acc.y)) < threshold || min(abs(acc.x), abs(acc.y)) / max(abs(acc.x), abs(acc.y)) < 0.55f) {
        return false
    }
    val half = 0f
    return when (corner) {
        "corner_tl" -> acc.x > half && acc.y > half
        "corner_tr" -> acc.x < half && acc.y > half
        "corner_bl" -> acc.x > half && acc.y < half
        "corner_br" -> acc.x < half && acc.y < half
        else -> false
    }
}
