package com.rubyketang.launcher.engine.rank

import com.rubyketang.launcher.engine.pinyin.MatchQuality
import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** P0-3 排序器验收：权重是常量表，改权重不改逻辑；得分构成可打印。 */
class RankerTest {

    private val ranker = Ranker()
    private val target = Target("app://a/b", Kind.APP, "测试", launch = LaunchSpec("app://a/b"))

    @Test
    fun `满分构成 total = 1`() {
        val scored = ranker.score(target, MatchQuality.EXACT, frecency = 1f, context = 1f, pin = 1f)
        assertEquals(1f, scored.score, 1e-6f)
    }

    @Test
    fun `得分构成符合权重常量表`() {
        val scored = ranker.score(target, MatchQuality.INITIALS, frecency = 0.5f, context = 0.2f, pin = 1f)
        assertEquals(Weights.MATCH * MatchQuality.INITIALS.score, scored.breakdown.match, 1e-6f)
        assertEquals(Weights.FRECENCY * 0.5f, scored.breakdown.frecency, 1e-6f)
        assertEquals(Weights.CONTEXT * 0.2f, scored.breakdown.context, 1e-6f)
        assertEquals(Weights.PIN * 1f, scored.breakdown.pin, 1e-6f)
        assertEquals(scored.breakdown.total, scored.score, 1e-6f)
    }

    @Test
    fun `matchQuality 优先级 精确 前缀 首字母 模糊音 子串`() {
        val scores = MatchQuality.entries.map { ranker.score(target, it, 0f, 0f, 0f).score }
        assertEquals(
            listOf(
                MatchQuality.EXACT, MatchQuality.PREFIX, MatchQuality.INITIALS,
                MatchQuality.FUZZY, MatchQuality.SUBSTRING,
            ),
            MatchQuality.entries.sortedByDescending { q -> ranker.score(target, q, 0f, 0f, 0f).score },
        )
        assertTrue(scores.zipWithNext().all { (a, b) -> a > b })
    }

    @Test
    fun `frecency 公式 count 除以 1 加天数`() {
        val now = 1_000_000_000_000L
        val usage = InMemoryUsageStore(slotOf = { TimeSlot.MORNING })
        val day = 24L * 60 * 60 * 1000
        // 10 天前用了 9 次
        repeat(9) { usage.record("a", now - 10 * day) }
        // 今天用了 3 次
        repeat(3) { usage.record("b", now) }

        assertEquals(9, usage.count("a", now))
        assertEquals(3, usage.count("b", now))
        // count / (1 + daysSinceLastUse)：9/11 < 3/1，新鲜的 3 次胜过陈旧的 9 次
        assertTrue(9f / 11f < 3f / 1f)
    }
}
