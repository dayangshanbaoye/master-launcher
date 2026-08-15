package com.rubyketang.launcher.engine.rank

import com.rubyketang.launcher.engine.pinyin.MatchQuality
import com.rubyketang.launcher.model.MatchReason
import com.rubyketang.launcher.model.ScoreBreakdown
import com.rubyketang.launcher.model.ScoredTarget
import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.Target

/**
 * P0-3 权重常量表。改权重只动这里，不改 Ranker 逻辑。
 * score = 0.45·matchQuality + 0.30·frecency + 0.15·context + 0.10·pin
 */
object Weights {
    const val MATCH = 0.45f
    const val FRECENCY = 0.30f
    const val CONTEXT = 0.15f
    const val PIN = 0.10f
}

/**
 * 排序器。只消费匹配得分（拼音在 PinyinEngine），输入分量均为 0..1。
 */
class Ranker {

    /**
     * @param quality  匹配质量，null 表示非文本查询（Browse / 手势 / 空查询）
     * @param frecency 归一化后的 frecency（0..1）
     * @param context  时段亲和度（0..1）
     * @param pin      0 或 1
     */
    fun score(
        target: Target,
        quality: MatchQuality?,
        frecency: Float,
        context: Float,
        pin: Float,
    ): ScoredTarget {
        val breakdown = ScoreBreakdown(
            match = Weights.MATCH * (quality?.score ?: 0f),
            frecency = Weights.FRECENCY * frecency,
            context = Weights.CONTEXT * context,
            pin = Weights.PIN * pin,
        )
        return ScoredTarget(
            target = target,
            score = breakdown.total,
            reason = reasonOf(target, quality),
            breakdown = breakdown,
        )
    }

    private fun reasonOf(target: Target, quality: MatchQuality?): MatchReason = when {
        target.kind == Kind.SHORTCUT -> MatchReason("快捷方式")
        quality != null -> MatchReason(quality.reason)
        else -> MatchReason("常用")
    }
}
