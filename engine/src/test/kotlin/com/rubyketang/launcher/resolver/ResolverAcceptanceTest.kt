package com.rubyketang.launcher.resolver

import com.rubyketang.launcher.engine.index.InMemoryIndexStore
import com.rubyketang.launcher.engine.pinyin.DefaultPinyinEngine
import com.rubyketang.launcher.engine.pinyin.Pinyin4jDict
import com.rubyketang.launcher.engine.rank.ContextSignals
import com.rubyketang.launcher.engine.rank.InMemoryUsageStore
import com.rubyketang.launcher.engine.rank.Ranker
import com.rubyketang.launcher.engine.rank.TimeSlot
import com.rubyketang.launcher.engine.visibility.TagDndRegistry
import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.Tag
import com.rubyketang.launcher.model.Target
import com.rubyketang.launcher.source.TargetSource
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P0-2 / P0-4 整链路验收：Query → Resolver → IndexStore → PinyinEngine → Ranker。
 */
class ResolverAcceptanceTest {

    private fun app(id: String, label: String, tags: Set<Tag> = emptySet()) =
        Target("app://$id", Kind.APP, label, tags = tags, launch = LaunchSpec("app://$id"))

    private val reading = Tag("阅读")

    private val corpus = listOf(
        app("com.tencent.mm", "微信"),
        app("com.tencent.weread", "微信读书", setOf(reading)),
        app("com.autonavi.minimap", "高德地图"),
        app("com.eg.android.AlipayGphone", "支付宝"),
        app("com.taobao.taobao", "淘宝"),
        app("tv.danmaku.bili", "哔哩哔哩"),
        app("com.ss.android.ugc.aweme", "抖音"),
        app("com.netease.cloudmusic", "网易云音乐"),
        app("com.amazon.kindlefc", "Kindle", setOf(reading)),
    )

    private val pinyin = DefaultPinyinEngine(Pinyin4jDict())
    private val now = 1_800_000_000_000L

    private fun newResolver(
        usage: InMemoryUsageStore = InMemoryUsageStore(slotOf = { TimeSlot.MORNING }),
        dnd: TagDndRegistry = TagDndRegistry(),
    ): Resolver {
        val store = InMemoryIndexStore(listOf(TargetSource { corpus }), pinyin)
        runBlocking { store.rebuild() }
        assertEquals(corpus.size, store.observe().value.size)
        return Resolver(
            store = store,
            pinyin = pinyin,
            usage = usage,
            context = { ContextSignals(slot = TimeSlot.MORNING) },
            ranker = Ranker(),
            dnd = dnd,
            clock = { now },
        )
    }

    @Test
    fun `wxdsh 微信读书 排第一`() {
        val results = newResolver().resolve(Query(text = "wxdsh"))
        assertEquals("微信读书", results.first().target.label)
        assertEquals("首字母", results.first().reason.display)
    }

    @Test
    fun `weixin 微信 排第一`() {
        val results = newResolver().resolve(Query(text = "weixin"))
        assertEquals("微信", results.first().target.label)
        // 微信读书 也命中（全拼前缀），但精确优先
        assertEquals(listOf("微信", "微信读书"), results.take(2).map { it.target.label })
    }

    @Test
    fun `dtu 与 ditu 均命中 高德地图`() {
        val dtu = newResolver().resolve(Query(text = "dtu"))
        assertTrue(dtu.any { it.target.label == "高德地图" }, "dtu 未命中高德地图: $dtu")
        val ditu = newResolver().resolve(Query(text = "ditu"))
        assertTrue(ditu.any { it.target.label == "高德地图" }, "ditu 未命中高德地图: $ditu")
    }

    @Test
    fun `读书 原文子串命中 微信读书`() {
        val results = newResolver().resolve(Query(text = "读书"))
        assertTrue(results.any { it.target.label == "微信读书" }, "读书 未命中微信读书: $results")
    }

    @Test
    fun `P1-6 无命中时末条兜底 在浏览器中搜索`() {
        val results = newResolver().resolve(Query(text = "zzzzz"))
        assertEquals(1, results.size)
        assertEquals(Kind.WEB, results[0].target.kind)
        assertEquals("在浏览器中搜索 zzzzz", results[0].target.label)
        assertTrue(results[0].target.launch.uri.startsWith("https://"))
    }

    @Test
    fun `P1-2 sys 命中 微信扫一扫 快捷方式`() {
        val withShortcut = corpus + Target(
            id = "shortcut://com.tencent.mm/scan",
            kind = Kind.SHORTCUT,
            label = "微信 → 扫一扫",
            launch = LaunchSpec("shortcut://com.tencent.mm/scan"),
        )
        val store = InMemoryIndexStore(listOf(TargetSource { withShortcut }), pinyin)
        runBlocking { store.rebuild() }
        val resolver = Resolver(
            store = store,
            pinyin = pinyin,
            usage = InMemoryUsageStore(slotOf = { TimeSlot.MORNING }),
            context = { ContextSignals(slot = TimeSlot.MORNING) },
            ranker = Ranker(),
            clock = { now },
        )
        val results = resolver.resolve(Query(text = "sys"))
        assertTrue(results.any { it.target.kind == Kind.SHORTCUT && it.target.label == "微信 → 扫一扫" },
            "sys 未命中快捷方式: $results")
        assertEquals("快捷方式", results.first { it.target.kind == Kind.SHORTCUT }.reason.display)
    }

    @Test
    fun `tag 查询只含该分类 按 frecency 排序`() {
        val usage = InMemoryUsageStore(slotOf = { TimeSlot.MORNING })
        val resolver = newResolver(usage)
        // Kindle 今天用了 3 次
        repeat(3) { usage.record("app://com.amazon.kindlefc", now) }
        val results = resolver.resolve(Query(tag = reading))
        assertEquals(setOf("微信读书", "Kindle"), results.map { it.target.label }.toSet())
        assertEquals("Kindle", results.first().target.label)
        assertEquals("今天 3 次", results.first().reason.display)
    }

    @Test
    fun `P2-2 免打扰分类会从所有 Resolver 路径隐藏，到期后恢复`() {
        val dnd = TagDndRegistry().apply { hide(reading, now + 1_000L) }
        val resolver = newResolver(dnd = dnd)

        assertTrue(resolver.resolve(Query()).none { reading in it.target.tags })
        assertTrue(resolver.resolve(Query(tag = reading)).isEmpty())

        // 相同 Registry 读取未来时惰性恢复，无需重建索引。
        assertTrue(dnd.isVisible(corpus.first { it.label == "微信读书" }, now + 1_000L))
    }

    @Test
    fun `launch 记账后影响空查询排序`() {
        val resolver = newResolver()
        val amap = corpus.first { it.label == "高德地图" }
        repeat(5) { resolver.launch(amap) }
        val results = resolver.resolve(Query())
        assertEquals("高德地图", results.first().target.label)
    }

    @Test
    fun `得分构成可打印`() {
        val first = newResolver().resolve(Query(text = "wxdsh")).first()
        println("微信读书 breakdown: ${first.breakdown}")
        assertTrue(first.breakdown.match > 0f)
    }

    @Test
    fun `500 条目单次 query 小于 8ms`() {
        // 生成 500 条目的语料：真实汉字组合 + 验收条目
        val chars = "微信读书高德地图支付宝淘宝哔哩抖音网易云端音乐视频浏览器文件管理器天气日历时钟计算器相机图库设置电话短信联系人应用商店游戏中心"
        val big = (1..500).map { i ->
            val label = buildString {
                val n = 2 + i % 3
                repeat(n) { j -> append(chars[(i * 7 + j * 13) % chars.length]) }
            }
            app("com.generated.$i", label)
        } + corpus

        val store = InMemoryIndexStore(listOf(TargetSource { big }), pinyin)
        runBlocking { store.rebuild() }
        assertEquals(big.size, store.observe().value.size)
        val resolver = Resolver(
            store = store,
            pinyin = pinyin,
            usage = InMemoryUsageStore(slotOf = { TimeSlot.MORNING }),
            context = { ContextSignals(slot = TimeSlot.MORNING) },
            ranker = Ranker(),
            clock = { now },
        )

        val queries = listOf("wxdsh", "weixin", "dtu", "读书", "ditu", "zzz")
        // 预热 JIT
        repeat(50) { queries.forEach { q -> resolver.resolve(Query(text = q)) } }

        val iterations = 200
        val start = System.nanoTime()
        repeat(iterations) { queries.forEach { q -> resolver.resolve(Query(text = q)) } }
        val avgMillis = (System.nanoTime() - start) / 1e6 / (iterations * queries.size)
        println("500 条目平均单次 query: %.3f ms".format(avgMillis))
        assertTrue(avgMillis < 8.0, "单次 query 超预算: %.3f ms".format(avgMillis))

        // 大语料下验收结论不变
        assertEquals("微信读书", resolver.resolve(Query(text = "wxdsh")).first().target.label)
        assertEquals("微信", resolver.resolve(Query(text = "weixin")).first().target.label)
    }
}
