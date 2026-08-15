package com.rubyketang.launcher.engine.showcase

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 05-product-spec.md §2.5 海报模式切换时机："每次解锁（默认）/ 每天一次 / 手动"。
 * 纯逻辑，不碰 Bitmap/Uri——只算"解锁时该显示第几张"。
 *
 * 相册墙模式不消费这个策略——"墙本身不翻页、不动，全部照片一次性呈现"（§2.5），
 * 只有海报模式的单图轮换需要它。
 */
class ShowcaseRotationPolicyTest {

    private val day = 24L * 60 * 60 * 1000
    private val policy = ShowcaseRotationPolicy()

    @Test
    fun `首次解锁只记录基准日 显示第一张不前进`() {
        assertEquals(0, policy.onUnlock(photoCount = 5, timing = ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0))
    }

    @Test
    fun `每次解锁模式 第二次解锁起前进到下一张`() {
        policy.onUnlock(5, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0)
        assertEquals(1, policy.onUnlock(5, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0))
        assertEquals(2, policy.onUnlock(5, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 1_000))
    }

    @Test
    fun `每次解锁模式 到末张后循环回第一张`() {
        policy.onUnlock(3, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0) // 第 1 次：index 0
        policy.onUnlock(3, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0) // index 1
        policy.onUnlock(3, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0) // index 2
        assertEquals(0, policy.onUnlock(3, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0)) // 循环回 0
    }

    @Test
    fun `每天一次模式 同一天内反复解锁不重复前进`() {
        policy.onUnlock(5, ShowcaseSwitchTiming.DAILY, now = 0) // 第 1 次：基准日
        val first = policy.onUnlock(5, ShowcaseSwitchTiming.DAILY, now = 1_000)
        val second = policy.onUnlock(5, ShowcaseSwitchTiming.DAILY, now = 2_000)
        assertEquals(first, second, "同一天内不应该继续前进")
    }

    @Test
    fun `每天一次模式 跨天后前进一张`() {
        policy.onUnlock(5, ShowcaseSwitchTiming.DAILY, now = 0) // 基准日
        val sameDay = policy.onUnlock(5, ShowcaseSwitchTiming.DAILY, now = day - 1)
        val nextDay = policy.onUnlock(5, ShowcaseSwitchTiming.DAILY, now = day)
        assertEquals(sameDay, sameDay) // 占位，突出下方才是关键断言
        assertEquals((sameDay + 1) % 5, nextDay)
    }

    @Test
    fun `手动模式 解锁永不自动前进`() {
        policy.onUnlock(5, ShowcaseSwitchTiming.MANUAL, now = 0)
        repeat(5) { policy.onUnlock(5, ShowcaseSwitchTiming.MANUAL, now = it * day) }
        assertEquals(0, policy.onUnlock(5, ShowcaseSwitchTiming.MANUAL, now = 100L * day))
    }

    @Test
    fun `手动 next 前进 到末张后循环`() {
        assertEquals(1, policy.next(3))
        assertEquals(2, policy.next(3))
        assertEquals(0, policy.next(3))
    }

    @Test
    fun `手动 previous 从第一张反向循环到最后一张`() {
        assertEquals(2, policy.previous(3))
        assertEquals(1, policy.previous(3))
    }

    @Test
    fun `文件夹图片变少后 越界的当前索引被纠正到范围内`() {
        policy.onUnlock(10, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0)
        repeat(8) { policy.onUnlock(10, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0) } // index 走到 8
        assertEquals(8 % 3, policy.currentIndex(photoCount = 3))
    }

    @Test
    fun `没有照片时索引恒为 0`() {
        assertEquals(0, policy.onUnlock(photoCount = 0, timing = ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0))
        assertEquals(0, policy.currentIndex(photoCount = 0))
    }

    @Test
    fun `快照可以还原状态 不用重新从第一张开始`() {
        policy.onUnlock(5, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0) // index 0，基准日 0
        policy.onUnlock(5, ShowcaseSwitchTiming.EVERY_UNLOCK, now = 0) // index 1
        val saved = policy.snapshot()

        val restored = ShowcaseRotationPolicy()
        restored.restore(saved)
        assertEquals(1, restored.currentIndex(5))
        // 还原后按每天一次模式解锁，因为基准日没变，同一天不应该前进
        assertEquals(1, restored.onUnlock(5, ShowcaseSwitchTiming.DAILY, now = 0))
    }
}
