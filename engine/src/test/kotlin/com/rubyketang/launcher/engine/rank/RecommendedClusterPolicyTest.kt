package com.rubyketang.launcher.engine.rank

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 05-product-spec.md §2.3 推荐簇迟滞规则：
 * "替换在位者的条件：连续 3 天 frecency 得分 > 在位者 × 1.2 才换"。
 * 纯 top-N 会让位置天天变，用户建不起肌肉记忆——引擎越勤快，这一栏越没法闭眼点。
 *
 * 用"跨自然日调用"模拟真实使用：每天只调一次 resolve（对应 Canvas 每天首次打开时重新核算），
 * 同一天内重复调用不重复计数。
 */
class RecommendedClusterPolicyTest {

    private val day = 24L * 60 * 60 * 1000
    private val policy = RecommendedClusterPolicy()

    @Test
    fun `空槽直接取候选池最高分 不需要迟滞`() {
        val result = policy.resolve(slot = 0, occupantId = null, occupantScore = 0f, candidates = listOf("a" to 1f, "b" to 2f), now = 0)
        assertEquals("b", result)
    }

    @Test
    fun `挑战者分数不足 1_2 倍 在位者不变`() {
        // 占位者分 1.0，挑战者 1.19（差一点点不够）
        val result = policy.resolve(slot = 0, occupantId = "occupant", occupantScore = 1f, candidates = listOf("challenger" to 1.19f), now = 0)
        assertEquals("occupant", result)
    }

    @Test
    fun `连续 2 天达标 第 3 天才换`() {
        var now = 0L
        // 第 1 天：达标，计数 1
        assertEquals("occupant", policy.resolve(1, "occupant", 1f, listOf("challenger" to 1.21f), now))
        // 第 2 天：达标，计数 2
        now += day
        assertEquals("occupant", policy.resolve(1, "occupant", 1f, listOf("challenger" to 1.21f), now))
        // 第 3 天：达标，计数 3 → 换
        now += day
        assertEquals("challenger", policy.resolve(1, "occupant", 1f, listOf("challenger" to 1.21f), now))
    }

    @Test
    fun `同一天内反复调用 不重复计数`() {
        var now = 0L
        policy.resolve(2, "occupant", 1f, listOf("challenger" to 1.21f), now)
        now += day
        policy.resolve(2, "occupant", 1f, listOf("challenger" to 1.21f), now)
        // 同一天内再调 5 次，不应该提前把计数推到 3
        repeat(5) { policy.resolve(2, "occupant", 1f, listOf("challenger" to 1.21f), now) }
        // 第 3 天才应该真正换
        assertEquals("occupant", policy.resolve(2, "occupant", 1f, listOf("challenger" to 1.21f), now))
        now += day
        assertEquals("challenger", policy.resolve(2, "occupant", 1f, listOf("challenger" to 1.21f), now))
    }

    @Test
    fun `连续中断一天 计数重新开始`() {
        var now = 0L
        policy.resolve(3, "occupant", 1f, listOf("challenger" to 1.21f), now) // day0：计数1
        now += day
        policy.resolve(3, "occupant", 1f, listOf("challenger" to 1.21f), now) // day1：计数2
        now += 3 * day // 跳过 day2，直接到 day4：断档
        val result = policy.resolve(3, "occupant", 1f, listOf("challenger" to 1.21f), now)
        assertEquals("occupant", result, "断档后应该从计数 1 重新开始，不应该在这一天就换")
    }

    @Test
    fun `换人后立刻重置计数 不会紧接着又被换回去`() {
        var now = 0L
        repeat(3) {
            policy.resolve(4, "occupant", 1f, listOf("challenger" to 1.21f), now)
            now += day
        }
        // 上面第三次调用（now 已经是第 3 天）应该已经换成 challenger；这里用换人后的新在位者再验证一次
        now -= day // 回到刚换完的那一天
        val afterSwap = policy.resolve(4, "challenger", 1f, listOf("occupant" to 1.21f), now)
        // 新挑战者（原 occupant）刚测第 1 天，不该立刻又换回去
        assertEquals("challenger", afterSwap)
    }

    @Test
    fun `换人条件是严格大于 1_2 倍 恰好等于不算`() {
        var now = 0L
        repeat(3) {
            assertEquals("occupant", policy.resolve(5, "occupant", 1f, listOf("challenger" to 1.2f), now))
            now += day
        }
    }

    @Test
    fun `快照可以还原状态 不用重新累计天数`() {
        var now = 0L
        policy.resolve(6, "occupant", 1f, listOf("challenger" to 1.21f), now)
        now += day
        policy.resolve(6, "occupant", 1f, listOf("challenger" to 1.21f), now)
        val saved = policy.snapshot()

        val restored = RecommendedClusterPolicy()
        restored.restore(saved)
        now += day
        // 还原后接着第 3 天检查，应该直接换，证明前两天的计数被还原保留了
        assertEquals("challenger", restored.resolve(6, "occupant", 1f, listOf("challenger" to 1.21f), now))
    }
}
