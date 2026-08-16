package com.rubyketang.launcher.engine.tag

import kotlin.math.abs

/**
 * [TagResolver] 第 5 层"行为聚类"用的建议来源。只读，`hintFor` 命中即视为一条建议，
 * 由 [TagResolver] 在关键词规则也没命中时消费；未命中返回 null，交给兜底层。
 */
fun interface BehaviorClusterHints {
    fun hintFor(targetId: String): String?

    companion object {
        val NONE = BehaviorClusterHints { null }
    }
}

/**
 * 05-product-spec.md §3.2.1 第 5 层：行为聚类。
 *
 * 统计"未分类"条目与已有分类条目的连续启动关系——A 打开后 [coOccurrenceWindowMillis] 内常开 B，
 * 就用 B 已知分类给 A 投票；某个分类的票数达到 [minEvidence] 才采纳，避免偶然的一两次巧合就定性。
 *
 * 只读 usage 快照 + 当前已知分类做输入，纯函数、无副作用，可在 JVM 单测里验证；
 * **只用于给未分类项打标或提示改标，不推翻已有分类**——调用方必须只把已分类条目放进 [knownTags]，
 * 已分类的条目不会出现在返回的建议里（见 [build] 的 unclassified 过滤）。
 */
class BehaviorClusterModel(
    private val coOccurrenceWindowMillis: Long = FIVE_MINUTES_MILLIS,
    private val minEvidence: Int = DEFAULT_MIN_EVIDENCE,
) {
    /**
     * @param usageByTarget targetId → 该条目的启动时刻（毫秒），一般来自 UsageStore 快照
     * @param knownTags targetId → 已经确定的分类名（只放已分类条目；未分类条目不要放进来做证据）
     */
    fun build(usageByTarget: Map<String, List<Long>>, knownTags: Map<String, String>): BehaviorClusterHints {
        val unclassified = usageByTarget.keys - knownTags.keys
        if (unclassified.isEmpty()) return BehaviorClusterHints.NONE

        val timeline = knownTags.keys
            .flatMap { id -> usageByTarget[id].orEmpty().map { it to id } }
            .sortedBy { it.first }
        if (timeline.isEmpty()) return BehaviorClusterHints.NONE

        val suggestions = HashMap<String, String>()
        for (id in unclassified) {
            val times = usageByTarget[id].orEmpty()
            if (times.isEmpty()) continue
            val votes = HashMap<String, Int>()
            for (t in times) {
                for ((otherTime, otherId) in timeline) {
                    if (abs(otherTime - t) > coOccurrenceWindowMillis) continue
                    val tag = knownTags.getValue(otherId)
                    votes[tag] = (votes[tag] ?: 0) + 1
                }
            }
            val (bestTag, bestCount) = votes.maxByOrNull { it.value } ?: continue
            if (bestCount >= minEvidence) suggestions[id] = bestTag
        }

        if (suggestions.isEmpty()) return BehaviorClusterHints.NONE
        return BehaviorClusterHints { suggestions[it] }
    }

    private companion object {
        const val FIVE_MINUTES_MILLIS = 5 * 60 * 1000L
        const val DEFAULT_MIN_EVIDENCE = 3
    }
}
