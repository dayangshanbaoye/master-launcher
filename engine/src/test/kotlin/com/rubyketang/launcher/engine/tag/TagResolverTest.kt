package com.rubyketang.launcher.engine.tag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 05-product-spec.md §3.2.1–§3.2.3 分类生成 + 编辑验收。 */
class TagResolverTest {

    private val resolver = TagResolver()

    private fun names(targetId: String, label: String = "", packageName: String = "com.unknown.app", androidCategory: Int = -1) =
        resolver.resolve(targetId, label, packageName, androidCategory).map { it.name }.toSet()

    // ---- §3.2.1 六层来源，按优先级 ----

    @Test
    fun `内置映射表优先于 ApplicationInfo category`() {
        // com.tencent.weread 在映射表命中"阅读"，即使 androidCategory 传游戏(0) 也不应该被覆盖
        assertEquals(setOf("阅读"), names("app://a/b", packageName = "com.tencent.weread", androidCategory = 0))
    }

    @Test
    fun `映射表未命中时走 ApplicationInfo category`() {
        // CATEGORY_SOCIAL = 4
        assertEquals(setOf("社交"), names("app://a/b", packageName = "com.example.chat", androidCategory = 4))
    }

    @Test
    fun `映射表和 category 都未命中时走名称关键词规则`() {
        assertEquals(setOf("阅读"), names("app://a/b", label = "深夜读书", packageName = "com.unknown.reader"))
        assertEquals(setOf("金融"), names("app://a/b", label = "掌上银行", packageName = "com.unknown.bank"))
        assertEquals(setOf("金融"), names("app://a/c", label = "扫码支付", packageName = "com.unknown.pay"))
    }

    @Test
    fun `六层全不命中时兜底未分类`() {
        assertEquals(setOf("未分类"), names("app://a/b", label = "随便一个东西", packageName = "com.unknown.app"))
    }

    @Test
    fun `行为聚类建议在关键词规则未命中时生效`() {
        resolver.installClusterHints(BehaviorClusterHints { id -> if (id == "app://a/b") "出行" else null })
        assertEquals(setOf("出行"), names("app://a/b", label = "随便一个东西"))
    }

    @Test
    fun `关键词规则优先于行为聚类建议`() {
        resolver.installClusterHints(BehaviorClusterHints { "出行" })
        assertEquals(setOf("阅读"), names("app://a/b", label = "深夜读书"))
    }

    @Test
    fun `行为聚类建议不是已知分类时忽略走兜底`() {
        resolver.installClusterHints(BehaviorClusterHints { "不存在的分类" })
        assertEquals(setOf("未分类"), names("app://a/b", label = "随便一个东西"))
    }

    // ---- 用户覆盖：最高优先级、可持久化 ----

    @Test
    fun `用户覆盖优先级最高且可持久化恢复`() {
        resolver.toggleTag("app://a/b", currentTags = emptySet(), category = "工具")
        assertEquals(setOf("工具"), names("app://a/b", packageName = "com.tencent.weread"))

        val restored = TagResolver()
        restored.restore(resolver.overrides())
        assertEquals(setOf("工具"), restored.resolve("app://a/b", "", "com.tencent.weread", -1).map { it.name }.toSet())
    }

    // ---- §3.2.2/§3.2.3 多分类归属编辑 ----

    @Test
    fun `勾选未持有的分类时追加为多归属`() {
        resolver.toggleTag("app://a/b", currentTags = setOf("阅读"), category = "工具")
        assertEquals(setOf("阅读", "工具"), names("app://a/b", packageName = "com.tencent.weread"))
    }

    @Test
    fun `取消勾选已持有的分类时移除`() {
        resolver.toggleTag("app://a/b", currentTags = setOf("阅读", "工具"), category = "工具")
        assertEquals(setOf("阅读"), names("app://a/b", packageName = "com.tencent.weread"))
    }

    @Test
    fun `移除最后一个分类后回落未分类`() {
        resolver.toggleTag("app://a/b", currentTags = setOf("阅读"), category = "阅读")
        assertEquals(setOf("未分类"), names("app://a/b", packageName = "com.tencent.weread"))
    }

    @Test
    fun `勾选未知分类名抛出异常`() {
        assertFailsWith<IllegalArgumentException> {
            resolver.toggleTag("app://a/b", currentTags = emptySet(), category = "我的收藏")
        }
    }

    @Test
    fun `批量添加分类只影响选中的条目`() {
        resolver.addTag("app://a/1", currentTags = emptySet(), category = "购物")
        resolver.addTag("app://a/2", currentTags = setOf("阅读"), category = "购物")

        assertEquals(setOf("购物"), names("app://a/1"))
        assertEquals(setOf("阅读", "购物"), names("app://a/2"))
    }

    @Test
    fun `批量移出分类为空时回落未分类`() {
        resolver.removeTag("app://a/1", currentTags = setOf("购物"), category = "购物")
        assertEquals(setOf("未分类"), names("app://a/1"))
    }

    // ---- 自定义分类：上限 8 个 ----

    @Test
    fun `自定义分类创建后可用于勾选`() {
        resolver.addCustomCategory("追剧")
        assertTrue("追剧" in resolver.allCategories())
        resolver.toggleTag("app://a/b", currentTags = emptySet(), category = "追剧")
        assertEquals(setOf("追剧"), names("app://a/b"))
    }

    @Test
    fun `自定义分类不能与已有分类重名`() {
        assertFailsWith<IllegalArgumentException> { resolver.addCustomCategory("阅读") }
    }

    @Test
    fun `自定义分类上限 8 个`() {
        repeat(8) { i -> resolver.addCustomCategory("自定义$i") }
        assertFailsWith<IllegalArgumentException> { resolver.addCustomCategory("第九个") }
    }

    @Test
    fun `自定义分类名单可持久化恢复`() {
        resolver.addCustomCategory("追剧")
        val restored = TagResolver()
        restored.restoreCustomCategories(resolver.customCategories())
        assertTrue("追剧" in restored.allCategories())
    }

    // ---- §3.2.3 Browse 左栏可见性：visibleCategories ----

    @Test
    fun `固定分类只要有一个条目就显示`() {
        assertEquals(listOf("阅读"), resolver.visibleCategories(mapOf("阅读" to 1)))
    }

    @Test
    fun `固定分类没有条目时不显示`() {
        assertEquals(emptyList(), resolver.visibleCategories(mapOf("阅读" to 0)))
        assertEquals(emptyList(), resolver.visibleCategories(emptyMap()))
    }

    @Test
    fun `自定义分类成员数小于 3 时不显示`() {
        resolver.addCustomCategory("追剧")
        assertEquals(emptyList(), resolver.visibleCategories(mapOf("追剧" to 2)))
    }

    @Test
    fun `自定义分类成员数达到 3 才显示`() {
        resolver.addCustomCategory("追剧")
        assertEquals(listOf("追剧"), resolver.visibleCategories(mapOf("追剧" to 3)))
    }

    @Test
    fun `固定分类在前 自定义分类在后 且按各自表内顺序排列`() {
        resolver.addCustomCategory("追剧")
        resolver.addCustomCategory("健身")
        val counts = mapOf("游戏" to 1, "阅读" to 1, "追剧" to 3, "健身" to 5)
        // ALL 表里"阅读"排在"游戏"前面（见 TagResolver.ALL），自定义分类按创建顺序排在固定表之后
        assertEquals(listOf("阅读", "游戏", "追剧", "健身"), resolver.visibleCategories(counts))
    }

    @Test
    fun `不含 全部 这个虚拟分类`() {
        val counts = TagResolver.ALL.associateWith { 1 }
        assertTrue(TagResolver.ALL_APPS !in resolver.visibleCategories(counts))
    }
}
