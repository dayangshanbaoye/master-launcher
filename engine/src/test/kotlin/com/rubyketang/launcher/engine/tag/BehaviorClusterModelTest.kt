package com.rubyketang.launcher.engine.tag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 05-product-spec.md §3.2.1 第 5 层"行为聚类"验收：
 * 统计未分类条目与已知分类条目的连续启动关系（A 打开后 N 分钟内常开 B），
 * 用邻居的已知分类给未分类条目打标建议；证据不足时不打标，交给兜底层。
 */
class BehaviorClusterModelTest {

    private val model = BehaviorClusterModel(coOccurrenceWindowMillis = 5 * 60 * 1000L, minEvidence = 3)

    @Test
    fun `未分类条目与出行类条目连续 3 次在 5 分钟内共同启动则建议出行`() {
        val base = 1_700_000_000_000L
        // "地图" 已知分类为出行；"打车"未分类，每次都在"地图"打开后 2 分钟内打开
        val usage = mapOf(
            "app://map" to listOf(base, base + 3 * 3600_000, base + 6 * 3600_000),
            "app://taxi" to listOf(base + 2 * 60_000, base + 3 * 3600_000 + 60_000, base + 6 * 3600_000 + 90_000),
        )
        val known = mapOf("app://map" to "出行")

        val hints = model.build(usage, known)

        assertEquals("出行", hints.hintFor("app://taxi"))
    }

    @Test
    fun `共现次数不足 3 次时不打标`() {
        val base = 1_700_000_000_000L
        val usage = mapOf(
            "app://map" to listOf(base, base + 3 * 3600_000),
            "app://taxi" to listOf(base + 60_000, base + 3 * 3600_000 + 60_000),
        )
        val known = mapOf("app://map" to "出行")

        val hints = model.build(usage, known)

        assertNull(hints.hintFor("app://taxi"))
    }

    @Test
    fun `超出时间窗口的共现不计入证据`() {
        val base = 1_700_000_000_000L
        val usage = mapOf(
            "app://map" to listOf(base, base + 3600_000, base + 2 * 3600_000),
            // 每次都晚 10 分钟，超过 5 分钟窗口
            "app://taxi" to listOf(base + 10 * 60_000, base + 3600_000 + 10 * 60_000, base + 2 * 3600_000 + 10 * 60_000),
        )
        val known = mapOf("app://map" to "出行")

        val hints = model.build(usage, known)

        assertNull(hints.hintFor("app://taxi"))
    }

    @Test
    fun `已有分类的条目不参与聚类打标`() {
        val base = 1_700_000_000_000L
        val usage = mapOf(
            "app://map" to listOf(base, base + 3600_000, base + 2 * 3600_000),
            "app://reader" to listOf(base + 60_000, base + 3600_000 + 60_000, base + 2 * 3600_000 + 60_000),
        )
        // reader 已经有分类，即使和 map 高度共现也不应被聚类改写——聚类只给未分类项打标
        val known = mapOf("app://map" to "出行", "app://reader" to "阅读")

        val hints = model.build(usage, known)

        assertNull(hints.hintFor("app://reader"))
    }

    @Test
    fun `没有使用数据时不产生任何建议`() {
        val hints = model.build(emptyMap(), emptyMap())
        assertNull(hints.hintFor("app://anything"))
    }

    @Test
    fun `多个候选分类时取证据最多的一个`() {
        val base = 1_700_000_000_000L
        val usage = mapOf(
            "app://map" to listOf(base, base + 3600_000, base + 2 * 3600_000),
            "app://weather" to listOf(base + 60_000, base + 3600_000 + 60_000),
            // taxi 与 map(出行) 共现 3 次，与 weather(资讯) 共现 2 次 —— 出行证据更强
            "app://taxi" to listOf(base + 90_000, base + 3600_000 + 90_000, base + 2 * 3600_000 + 90_000),
        )
        val known = mapOf("app://map" to "出行", "app://weather" to "资讯")

        val hints = model.build(usage, known)

        assertEquals("出行", hints.hintFor("app://taxi"))
    }
}
