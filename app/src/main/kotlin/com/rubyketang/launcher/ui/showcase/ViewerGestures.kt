package com.rubyketang.launcher.ui.showcase

/**
 * 05-product-spec.md §2.5 全屏查看器的判定参数，纯数值，不依赖 Compose/Android 类型，
 * 可用纯 JVM 单测覆盖。跟 [com.rubyketang.launcher.ui.gesture.GestureClassifier] 一个风格：
 * dp → px 的换算是调用方（Compose 层）的事，这里只吃已经换算好的 px。
 */
object ViewerGestureParams {
    /** 下滑距离达到可视区高度的这个比例就关闭，不用等手指划出屏幕。 */
    const val DISMISS_DISTANCE_FRACTION = 0.25f

    /** 距离不够时，松手瞬间的下滑速度超过这个阈值（px/s）也算数——照顾"快速轻甩"关不掉的挫败感。 */
    const val DISMISS_FLING_VELOCITY_PX_PER_SEC = 1400f

    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 4f
    const val DOUBLE_TAP_ZOOM = 2f
}

/**
 * 下滑关闭判定：只看松手那一刻的数值，不看过程——跟 GestureClassifier 的判定哲学一致。
 * 距离够了直接关；距离不够但甩得够快也关；否则弹回原位。上滑/未移动永远不关闭。
 */
object ViewerDismiss {
    fun shouldDismiss(dragDownPx: Float, viewportHeightPx: Float, releaseVelocityPxPerSec: Float): Boolean {
        if (dragDownPx <= 0f) return false
        if (viewportHeightPx > 0f && dragDownPx / viewportHeightPx >= ViewerGestureParams.DISMISS_DISTANCE_FRACTION) return true
        return releaseVelocityPxPerSec >= ViewerGestureParams.DISMISS_FLING_VELOCITY_PX_PER_SEC
    }
}

/** 双击 1x/2x 切换 + 双指捏合缩放/平移的钳制，纯函数，方便单测。 */
object ViewerZoom {
    /** 已经放大过（>1x）就双击回 1x；否则双击放到 2x。 */
    fun toggleForDoubleTap(currentScale: Float): Float =
        if (currentScale > ViewerGestureParams.MIN_ZOOM + ZOOM_EPSILON) ViewerGestureParams.MIN_ZOOM else ViewerGestureParams.DOUBLE_TAP_ZOOM

    fun clampScale(scale: Float): Float = scale.coerceIn(ViewerGestureParams.MIN_ZOOM, ViewerGestureParams.MAX_ZOOM)

    /**
     * 缩放后允许的平移范围：内容整体尺寸（原尺寸 × scale）比容器大出来的那一半，才是手指能拖动的
     * 空间；缩小回 1x 或内容本来就没容器大，平移量钳到 0——不允许内容边界露出容器空白。
     */
    fun clampOffset(offset: Float, contentSizePx: Float, containerSizePx: Float, scale: Float): Float {
        val scaledSize = contentSizePx * scale
        val maxOffset = ((scaledSize - containerSizePx) / 2f).coerceAtLeast(0f)
        return offset.coerceIn(-maxOffset, maxOffset)
    }

    private const val ZOOM_EPSILON = 0.01f
}
