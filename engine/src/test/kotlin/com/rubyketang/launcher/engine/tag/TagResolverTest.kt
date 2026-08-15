package com.rubyketang.launcher.engine.tag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** P0-9 分类生成验收。 */
class TagResolverTest {

    private val resolver = TagResolver()

    @Test
    fun `ApplicationInfo category 优先于内置映射表`() {
        // CATEGORY_SOCIAL = 4，即使包名不在映射表也命中
        assertEquals("社交", resolver.resolve("app://a/b", "com.example.chat", 4).name)
        // CATEGORY_GAME = 0
        assertEquals("游戏", resolver.resolve("app://a/b", "com.tencent.weread", 0).name)
    }

    @Test
    fun `category 未定义时走内置映射表`() {
        // CATEGORY_UNDEFINED = -1
        assertEquals("阅读", resolver.resolve("app://a/b", "com.tencent.weread", -1).name)
        assertEquals("出行", resolver.resolve("app://a/b", "com.autonavi.minimap", -1).name)
    }

    @Test
    fun `都不命中时兜底 其他`() {
        assertEquals("其他", resolver.resolve("app://a/b", "com.unknown.app", -1).name)
    }

    @Test
    fun `用户覆盖优先级最高且可持久化恢复`() {
        resolver.override("app://a/b", "工具")
        assertEquals("工具", resolver.resolve("app://a/b", "com.tencent.weread", -1).name)

        val restored = TagResolver()
        restored.restore(resolver.overrides())
        assertEquals("工具", restored.resolve("app://a/b", "com.tencent.weread", -1).name)
    }

    @Test
    fun `用户不能创建分类`() {
        assertFailsWith<IllegalArgumentException> { resolver.override("app://a/b", "我的收藏") }
    }
}
