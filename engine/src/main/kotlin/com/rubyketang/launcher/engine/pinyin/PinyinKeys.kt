package com.rubyketang.launcher.engine.pinyin

/**
 * 一个 label 预计算出的拼音键。构建期一次性生成，运行期只读。
 *
 * 三层索引：
 * - 全拼  [full]           微信读书 → "weixindushu"
 * - 首字母 [initials]                  → "wxds"
 * - 模糊音 [fuzzyFull]（z-zh, c-ch, s-sh, l-n, an-ang, en-eng, in-ing）
 */
class PinyinKeys(
    val label: String,
    /** 每字一个音节：汉字 → 拼音；可打印 ASCII → 其小写自身；其余字符原样。 */
    val syllables: List<String>,
) {
    val lowerLabel: String = label.lowercase()
    val full: String = syllables.joinToString("")
    val initials: String = syllables.joinToString("") { it.take(1) }
    val fuzzySyllables: List<String> = syllables.map(::fuzzy)
    val fuzzyFull: String = fuzzySyllables.joinToString("")
    val fuzzyInitials: String = fuzzySyllables.joinToString("") { it.take(1) }

    /** 从第 i 个字开始到结尾的全拼（支持从任意字边界起匹配，如 "ditu" → 高德地图）。 */
    val boundaryFulls: List<String> = syllables.indices.map { i -> syllables.subList(i, syllables.size).joinToString("") }
    val boundaryInitials: List<String> = syllables.indices.map { i -> syllables.subList(i, syllables.size).joinToString("") { it.take(1) } }
    val boundaryFuzzyFulls: List<String> = fuzzySyllables.indices.map { i -> fuzzySyllables.subList(i, fuzzySyllables.size).joinToString("") }
    val boundaryFuzzyInitials: List<String> = fuzzySyllables.indices.map { i -> fuzzySyllables.subList(i, fuzzySyllables.size).joinToString("") { it.take(1) } }

    companion object {
        /**
         * 索引键用的单一模糊音代表形式（全拍平）。
         * 只作用于索引键一侧（query 保持原样比对）。
         */
        fun fuzzy(syllable: String): String {
            var r = syllable
            if (r.startsWith("zh")) r = "z" + r.substring(2)
            else if (r.startsWith("ch")) r = "c" + r.substring(2)
            else if (r.startsWith("sh")) r = "s" + r.substring(2)
            if (r.startsWith("l")) r = "n" + r.substring(1)
            return when {
                r.endsWith("ang") -> r.dropLast(3) + "an"
                r.endsWith("eng") -> r.dropLast(3) + "en"
                r.endsWith("ing") -> r.dropLast(3) + "in"
                else -> r
            }
        }

        /**
         * 一个音节（或其前缀）的全部模糊音变体，含原形式。
         * 声母与韵尾独立组合：zh-z / c-ch / s-sh / l-n × an-ang / en-eng / in-ing，
         * 每个音节最多 4 个变体。匹配（consume）走这里。
         */
        fun fuzzyVariants(syllable: String): List<String> {
            val initials: List<Pair<String, String>> = when {
                syllable.startsWith("zh") -> listOf("zh", "z").map { it to syllable.drop(2) }
                syllable.startsWith("ch") -> listOf("ch", "c").map { it to syllable.drop(2) }
                syllable.startsWith("sh") -> listOf("sh", "s").map { it to syllable.drop(2) }
                syllable.startsWith("l") -> listOf("l", "n").map { it to syllable.drop(1) }
                else -> listOf("" to syllable)
            }
            return initials.flatMap { (init, rest) ->
                finalVariants(rest).map { init + it }
            }.distinct()
        }

        private fun finalVariants(rest: String): List<String> = when {
            rest.endsWith("ang") -> listOf(rest, rest.dropLast(3) + "an")
            rest.endsWith("eng") -> listOf(rest, rest.dropLast(3) + "en")
            rest.endsWith("ing") -> listOf(rest, rest.dropLast(3) + "in")
            rest.endsWith("an") -> listOf(rest, rest + "g")
            rest.endsWith("en") -> listOf(rest, rest + "g")
            rest.endsWith("in") -> listOf(rest, rest + "g")
            else -> listOf(rest)
        }
    }
}
