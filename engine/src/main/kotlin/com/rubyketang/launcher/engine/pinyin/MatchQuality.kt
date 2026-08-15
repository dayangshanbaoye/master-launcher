package com.rubyketang.launcher.engine.pinyin

/**
 * 匹配质量，决定 Ranker 的 match 分量。
 * 优先级：精确 > 前缀 > 首字母 > 模糊音 > 子串。
 */
enum class MatchQuality(val score: Float) {
    EXACT(1.0f),
    PREFIX(0.85f),
    INITIALS(0.70f),
    FUZZY(0.55f),
    SUBSTRING(0.40f);

    /** UI 用的最短表述。 */
    val reason: String
        get() = when (this) {
            EXACT -> "精确"
            PREFIX -> "前缀"
            INITIALS -> "首字母"
            FUZZY -> "模糊音"
            SUBSTRING -> "包含"
        }
}
