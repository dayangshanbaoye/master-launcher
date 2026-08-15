package com.rubyketang.launcher.engine.rank

/**
 * 05-product-spec.md §2.3 推荐簇迟滞规则：替换在位者的条件是"连续 3 天 frecency 得分 >
 * 在位者 × 1.2"，不是纯 top-N——纯 top-N 会让位置天天变，用户建不起肌肉记忆。
 *
 * 只管迟滞判定本身，不算分——调用方（LauncherState）把当天算好的分数传进来。按槽位（0..3）
 * 独立追踪状态，状态要靠调用方 [snapshot]/[restore] 持久化，"连续 N 天"必须跨会话累计。
 */
class RecommendedClusterPolicy {
    private val state = HashMap<Int, SlotState>()

    private data class SlotState(val challengerId: String?, val lastCheckedDay: Long, val streakDays: Int)

    /**
     * @param slot 槽位索引（0..3），每个槽独立追踪
     * @param occupantId 当前在位者，null 表示空槽
     * @param occupantScore 在位者当天分数（occupantId 为 null 时忽略）
     * @param candidates 候选池（调用方已排除固定簇成员和其它推荐槽的在位者），(id, 当天分数)
     * @param now 当前时刻（毫秒）
     * @return 该槽应该呈现的 target id；可能不变、可能换成新挑战者、也可能维持空槽
     */
    fun resolve(
        slot: Int,
        occupantId: String?,
        occupantScore: Float,
        candidates: List<Pair<String, Float>>,
        now: Long,
    ): String? {
        val today = now / DAY_MILLIS
        if (occupantId == null) {
            return candidates.maxByOrNull { it.second }?.first
        }
        val challenger = candidates.filter { it.first != occupantId }.maxByOrNull { it.second }
        if (challenger == null || challenger.second <= occupantScore * THRESHOLD) {
            state[slot] = SlotState(null, today, 0)
            return occupantId
        }
        val prev = state[slot]
        val streak = when {
            prev == null || prev.challengerId != challenger.first -> 1
            prev.lastCheckedDay == today -> prev.streakDays
            prev.lastCheckedDay == today - 1 -> prev.streakDays + 1
            else -> 1 // 中间断过（好几天没打开 app），重新计数
        }
        return if (streak >= REQUIRED_DAYS) {
            state[slot] = SlotState(null, today, 0) // 换完重置，避免新在位者立刻又被同一逻辑换回去
            challenger.first
        } else {
            state[slot] = SlotState(challenger.first, today, streak)
            occupantId
        }
    }

    /** 持久化快照：槽位 → (挑战者 id, 最后检查日, 连续天数)。 */
    fun snapshot(): Map<Int, Triple<String?, Long, Int>> =
        state.mapValues { (_, s) -> Triple(s.challengerId, s.lastCheckedDay, s.streakDays) }

    fun restore(saved: Map<Int, Triple<String?, Long, Int>>) {
        state.clear()
        saved.forEach { (slot, t) -> state[slot] = SlotState(t.first, t.second, t.third) }
    }

    companion object {
        const val THRESHOLD = 1.2f
        const val REQUIRED_DAYS = 3
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
