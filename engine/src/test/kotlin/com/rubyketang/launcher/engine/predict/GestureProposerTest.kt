package com.rubyketang.launcher.engine.predict

import com.rubyketang.launcher.engine.gesture.GestureRegistry
import com.rubyketang.launcher.engine.rank.ContextSignals
import com.rubyketang.launcher.engine.rank.InMemoryUsageStore
import com.rubyketang.launcher.engine.rank.TimeSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** P1-3 手势提议验收：连续 7 天 top-3 且未绑定 → 提议一次；拒绝后 30 天不提。 */
class GestureProposerTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private fun usageWithDailyTop3(): InMemoryUsageStore {
        val usage = InMemoryUsageStore(slotOf = { TimeSlot.MORNING })
        // 过去 7 天：a 每天用 5 次（稳 top-1），b 每天 3 次（top-2），c 每天 1 次（top-3）
        for (d in 1..7) {
            val base = now - d * day + day / 2
            repeat(5) { usage.record("a", base) }
            repeat(3) { usage.record("b", base) }
            repeat(1) { usage.record("c", base) }
        }
        return usage
    }

    @Test
    fun `连续 7 天 top-3 未绑定 提议使用量最高者`() {
        val proposer = GestureProposer(usageWithDailyTop3(), GestureRegistry())
        assertEquals("a", proposer.proposal(now))
    }

    @Test
    fun `已绑定手势的不提议`() {
        val gestures = GestureRegistry()
        gestures.bind("corner_br", "a")
        val proposer = GestureProposer(usageWithDailyTop3(), gestures)
        assertEquals("b", proposer.proposal(now))
    }

    @Test
    fun `拒绝后 30 天内不再提议同一目标`() {
        val usage = usageWithDailyTop3()
        val proposer = GestureProposer(usage, GestureRegistry())
        val first = proposer.proposal(now)!!
        proposer.reject(first, now)
        assertEquals("b", proposer.proposal(now))
        // 30 天冷却内：a 不再出现
        repeat(3) { usage.record("a", now + 10 * day) }
        val later = proposer.proposal(now + 10 * day)
        assert(later != first)
    }

    @Test
    fun `不足 7 天数据不提议`() {
        val usage = InMemoryUsageStore(slotOf = { TimeSlot.MORNING })
        repeat(10) { usage.record("a", now - day / 2) }
        assertNull(GestureProposer(usage, GestureRegistry()).proposal(now))
    }

    @Test
    fun `提议状态可持久化恢复`() {
        val usage = usageWithDailyTop3()
        val proposer = GestureProposer(usage, GestureRegistry())
        val first = proposer.proposal(now)!!
        proposer.reject(first, now)

        val (proposed, rejected) = proposer.snapshot()
        val restored = GestureProposer(usage, GestureRegistry())
        restored.restore(proposed, rejected)
        assertEquals("b", restored.proposal(now))
    }
}
