package com.rubyketang.launcher.model

/** 用于 UI 显示"首字母"/"今天 3 次"/"快捷方式"。 */
data class MatchReason(val display: String)

/**
 * 得分构成，调试用。
 * 各分量已经是乘过权重的贡献值，[total] 即最终得分。
 */
data class ScoreBreakdown(
    val match: Float,
    val frecency: Float,
    val context: Float,
    val pin: Float,
) {
    val total: Float get() = match + frecency + context + pin

    override fun toString(): String =
        "match=%.3f + frecency=%.3f + context=%.3f + pin=%.3f = %.3f"
            .format(match, frecency, context, pin, total)
}

data class ScoredTarget(
    val target: Target,
    val score: Float,
    val reason: MatchReason,
    val breakdown: ScoreBreakdown,
)
