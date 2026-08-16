package com.rubyketang.launcher.ui.showcase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 05-product-spec.md §2.5 全屏查看器判定参数验收：下滑关闭 / 双击缩放 / 缩放钳制。
 * 跟 GestureClassifierTest 一样，距离类阈值由调用方（Compose 层）换算成 px 后传入，
 * 这里只测判定逻辑本身，不碰 Compose 类型。
 */
class ViewerGesturesTest {

    // ---- 下滑关闭 ----

    @Test
    fun `下滑距离达到可视区 25% 时应关闭`() {
        assertTrue(ViewerDismiss.shouldDismiss(dragDownPx = 300f, viewportHeightPx = 1000f, releaseVelocityPxPerSec = 0f))
    }

    @Test
    fun `距离刚好卡在 25% 阈值下应保持打开`() {
        assertFalse(ViewerDismiss.shouldDismiss(dragDownPx = 249f, viewportHeightPx = 1000f, releaseVelocityPxPerSec = 0f))
    }

    @Test
    fun `距离不够 25% 但松手速度超过阈值时也应关闭`() {
        assertTrue(ViewerDismiss.shouldDismiss(dragDownPx = 50f, viewportHeightPx = 1000f, releaseVelocityPxPerSec = 2000f))
    }

    @Test
    fun `距离不够且速度也不够时应弹回原位`() {
        assertFalse(ViewerDismiss.shouldDismiss(dragDownPx = 50f, viewportHeightPx = 1000f, releaseVelocityPxPerSec = 100f))
    }

    @Test
    fun `没有下滑距离时永远不关闭 上滑或未移动都不触发`() {
        assertFalse(ViewerDismiss.shouldDismiss(dragDownPx = 0f, viewportHeightPx = 1000f, releaseVelocityPxPerSec = 5000f))
        assertFalse(ViewerDismiss.shouldDismiss(dragDownPx = -20f, viewportHeightPx = 1000f, releaseVelocityPxPerSec = 5000f))
    }

    // ---- 双击 1x/2x ----

    @Test
    fun `双击从 1x 放大到 2x`() {
        assertEquals(2f, ViewerZoom.toggleForDoubleTap(1f))
    }

    @Test
    fun `双击从任何放大状态回到 1x`() {
        assertEquals(1f, ViewerZoom.toggleForDoubleTap(2f))
        assertEquals(1f, ViewerZoom.toggleForDoubleTap(3.5f))
    }

    // ---- 缩放/平移钳制 ----

    @Test
    fun `缩放钳制在 1x 到 4x 之间`() {
        assertEquals(1f, ViewerZoom.clampScale(0.3f))
        assertEquals(4f, ViewerZoom.clampScale(10f))
        assertEquals(2.5f, ViewerZoom.clampScale(2.5f))
    }

    @Test
    fun `平移量不能让内容边界露出容器空白`() {
        // 内容放大到 2x 后比容器宽 400px，一半（200px）才是能拖的范围
        assertEquals(200f, ViewerZoom.clampOffset(500f, contentSizePx = 400f, containerSizePx = 400f, scale = 2f))
        assertEquals(-200f, ViewerZoom.clampOffset(-500f, contentSizePx = 400f, containerSizePx = 400f, scale = 2f))
    }

    @Test
    fun `缩放后内容仍比容器小时平移量钳到 0`() {
        assertEquals(0f, ViewerZoom.clampOffset(50f, contentSizePx = 100f, containerSizePx = 400f, scale = 1f))
    }
}
