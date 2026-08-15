package com.rubyketang.launcher.engine.browse

/** Browse 共同前缀计算：无相邻条目时明确返回 0。 */
object CommonPrefix {
    fun sharedPrefixLength(label: String, neighboringLabels: Iterable<String>): Int =
        neighboringLabels.maxOfOrNull { commonPrefix(label, it) } ?: 0

    private fun commonPrefix(a: String, b: String): Int {
        var index = 0
        while (index < a.length && index < b.length && a[index] == b[index]) index++
        return index
    }
}
