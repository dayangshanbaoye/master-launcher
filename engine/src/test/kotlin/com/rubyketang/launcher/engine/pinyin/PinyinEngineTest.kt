package com.rubyketang.launcher.engine.pinyin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** P0-2 拼音层验收。 */
class PinyinEngineTest {

    private val engine = DefaultPinyinEngine(Pinyin4jDict())

    @Test
    fun `wxdsh 混合首字母匹配 微信读书`() {
        val keys = engine.index("微信读书")
        assertEquals(MatchQuality.INITIALS, engine.match("wxdsh", keys))
    }

    @Test
    fun `wxds 纯首字母匹配 微信读书`() {
        val keys = engine.index("微信读书")
        assertEquals(MatchQuality.INITIALS, engine.match("wxds", keys))
    }

    @Test
    fun `weixin 是 微信 的全拼精确匹配`() {
        assertEquals(MatchQuality.EXACT, engine.match("weixin", engine.index("微信")))
    }

    @Test
    fun `weixin 是 微信读书 的全拼前缀`() {
        assertEquals(MatchQuality.PREFIX, engine.match("weixin", engine.index("微信读书")))
    }

    @Test
    fun `dtu 命中 高德地图 的 地图`() {
        val keys = engine.index("高德地图")
        assertEquals(MatchQuality.INITIALS, engine.match("dtu", keys))
    }

    @Test
    fun `ditu 命中 高德地图 的 地图全拼`() {
        val keys = engine.index("高德地图")
        assertEquals(MatchQuality.PREFIX, engine.match("ditu", keys))
    }

    @Test
    fun `读书 原文子串命中 微信读书`() {
        val keys = engine.index("微信读书")
        assertEquals(MatchQuality.SUBSTRING, engine.match("读书", keys))
    }

    @Test
    fun `模糊音 sanghai 命中 上海`() {
        val keys = engine.index("上海")
        assertEquals(MatchQuality.FUZZY, engine.match("sanghai", keys))
    }

    @Test
    fun `英文名按原文前缀匹配`() {
        val keys = engine.index("Kindle")
        assertEquals(MatchQuality.PREFIX, engine.match("kin", keys))
    }

    @Test
    fun `无关 query 不匹配`() {
        assertNull(engine.match("zzz", engine.index("微信读书")))
    }

    @Test
    fun `空 query 不匹配`() {
        assertNull(engine.match("", engine.index("微信读书")))
        assertNull(engine.match("   ", engine.index("微信读书")))
    }
}
