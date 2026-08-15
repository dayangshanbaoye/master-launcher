package com.rubyketang.launcher.engine.pinyin

/**
 * P0-2 拼音引擎。三层索引构建期生成（[index]），运行期只查表比对（[match]）。
 * 拼音匹配全部收敛在这里，Ranker 只消费返回的 MatchQuality。
 */
interface PinyinEngine {
    fun index(label: String): PinyinKeys
    fun match(query: String, keys: PinyinKeys): MatchQuality?
}

class DefaultPinyinEngine(private val dict: PinyinDict) : PinyinEngine {

    override fun index(label: String): PinyinKeys {
        val syllables = label.map { c ->
            when {
                dict.isHan(c) -> dict.pinyin(c) ?: c.toString()
                c in ' '..'~' -> c.lowercase()
                else -> c.toString()
            }
        }
        return PinyinKeys(label, syllables)
    }

    /**
     * 按质量从高到低依次尝试，返回第一个成立的层级：
     * 精确 > 前缀 > 首字母 > 模糊音 > 子串。
     */
    override fun match(query: String, keys: PinyinKeys): MatchQuality? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null

        // 精确：原文或全拼完全相等
        if (q == keys.lowerLabel || q == keys.full) return MatchQuality.EXACT

        // 前缀：原文前缀，或任一字符边界起的全拼前缀（"weixin" → 微信读书，"ditu" → 高德地图）
        if (keys.lowerLabel.startsWith(q)) return MatchQuality.PREFIX
        if (keys.boundaryFulls.any { it.startsWith(q) }) return MatchQuality.PREFIX

        // 首字母：从任一字边界起，query 能被逐字音节完整消费（每字至少 1 个字母）。
        // 允许混合消费："wxdsh" = w(微) x(信) d(读) sh(书)。
        if (consumeFromAnyBoundary(q, keys.syllables, fuzzy = false)) return MatchQuality.INITIALS

        // 模糊音：同上，但按模糊音归一化比对（"sanghai" → 上海）
        if (consumeFromAnyBoundary(q, keys.syllables, fuzzy = true)) return MatchQuality.FUZZY

        // 子串：原文（"读书" → 微信读书）或全拼包含
        if (keys.lowerLabel.contains(q) || keys.full.contains(q)) return MatchQuality.SUBSTRING

        return null
    }

    private fun consumeFromAnyBoundary(q: String, syllables: List<String>, fuzzy: Boolean): Boolean {
        for (start in syllables.indices) {
            if (consume(q, 0, syllables, start, fuzzy)) return true
        }
        return false
    }

    /**
     * 从 syllables[i] 开始消费 q[pos..]。每个字消费其音节的任意非空前缀
     * （模糊音层为该前缀的归一化形式），回溯搜索。
     */
    private fun consume(q: String, pos: Int, syllables: List<String>, i: Int, fuzzy: Boolean): Boolean {
        if (pos == q.length) return true
        if (i >= syllables.size) return false
        val s = syllables[i]
        val max = minOf(s.length, q.length - pos)
        for (k in max downTo 1) {
            if (segmentMatches(q, pos, k, s, fuzzy) && consume(q, pos + k, syllables, i + 1, fuzzy)) {
                return true
            }
        }
        return false
    }

    private fun segmentMatches(q: String, pos: Int, k: Int, syllable: String, fuzzy: Boolean): Boolean {
        if (q.regionMatches(pos, syllable, 0, k)) return true
        if (!fuzzy) return false
        // seg 等于该音节某一前缀的某个模糊音变体（变体可能更短，如 shang→sang/san）
        val seg = q.substring(pos, pos + k)
        for (m in 1..syllable.length) {
            if (seg in PinyinKeys.fuzzyVariants(syllable.substring(0, m))) return true
        }
        return false
    }
}
