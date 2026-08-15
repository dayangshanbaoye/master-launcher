package com.rubyketang.launcher.engine.predict

import com.rubyketang.launcher.engine.gesture.GestureRegistry
import com.rubyketang.launcher.engine.rank.UsageStore

/**
 * P1-3 手势提议（架构文档中 Predictor 的提议职责）。
 *
 * 规则：某 target 连续 7 天进入 top-3 且未绑定手势 → 提议一次；
 * 拒绝后 30 天内不再提议同一目标；已提议过（未被拒绝）不重复打扰。
 *
 * 卡片一次只提议一个绑定；用户不进任何界面也能获得手势。
 * 提议状态需持久化（snapshot/restore 由上层接 Proto DataStore）。
 */
class GestureProposer(
    private val usage: UsageStore,
    private val gestures: GestureRegistry,
) {
    private val proposedOnce = HashSet<String>()
    private val rejectedUntil = HashMap<String, Long>()

    /** 当前该提议的 target id；没有返回 null。一次只提议一个（取 7 天总量最高者）。 */
    fun proposal(now: Long): String? {
        val ids = usage.ids()
        if (ids.isEmpty()) return null
        val bound = gestures.bindings().values.toSet()

        return ids
            .asSequence()
            .filter { it !in bound }
            .filter { id ->
                if (id !in proposedOnce) return@filter true
                // 提议过：只有被拒绝且冷却结束才可再次出现
                val until = rejectedUntil[id] ?: return@filter false
                now >= until
            }
            .filter { id -> inTop3EveryDay(id, ids, now) }
            .maxByOrNull { id -> usage.countInRange(id, now - SEVEN_DAYS, now) }
    }

    /** 用户接受。绑定动作由上层完成（选哪个角是上层的事）。 */
    fun accept(targetId: String) {
        proposedOnce += targetId
        rejectedUntil -= targetId
    }

    /** 用户拒绝：30 天内不再提议同一目标。 */
    fun reject(targetId: String, now: Long) {
        proposedOnce += targetId
        rejectedUntil[targetId] = now + REJECT_COOLDOWN_MILLIS
    }

    fun snapshot(): Pair<Set<String>, Map<String, Long>> = proposedOnce.toSet() to rejectedUntil.toMap()

    fun restore(proposed: Set<String>, rejected: Map<String, Long>) {
        proposedOnce.clear(); proposedOnce += proposed
        rejectedUntil.clear(); rejectedUntil += rejected
    }

    private fun inTop3EveryDay(id: String, allIds: Set<String>, now: Long): Boolean {
        for (d in 1..7) {
            val from = now - d * DAY_MILLIS
            val top3 = allIds
                .map { it to usage.countInRange(it, from, from + DAY_MILLIS) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(3)
                .map { it.first }
            if (id !in top3) return false
        }
        return true
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val SEVEN_DAYS = 7 * DAY_MILLIS
        const val REJECT_COOLDOWN_MILLIS = 30 * DAY_MILLIS
    }
}
