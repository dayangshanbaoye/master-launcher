package com.rubyketang.launcher.resolver

import com.rubyketang.launcher.engine.gesture.GestureRegistry
import com.rubyketang.launcher.engine.index.IndexStore
import com.rubyketang.launcher.engine.index.IndexedTarget
import com.rubyketang.launcher.engine.pinyin.MatchQuality
import com.rubyketang.launcher.engine.pinyin.PinyinEngine
import com.rubyketang.launcher.engine.rank.ContextProvider
import com.rubyketang.launcher.engine.rank.ContextSignals
import com.rubyketang.launcher.engine.rank.PinStore
import com.rubyketang.launcher.engine.rank.Ranker
import com.rubyketang.launcher.engine.rank.UsageStore
import com.rubyketang.launcher.engine.tag.TagResolver
import com.rubyketang.launcher.engine.visibility.TagDndRegistry
import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.MatchReason
import com.rubyketang.launcher.model.ScoreBreakdown
import com.rubyketang.launcher.model.ScoredTarget
import com.rubyketang.launcher.model.Target

/**
 * P0-4 全系统唯一解析入口：Query → List<ScoredTarget>。
 * Search / Browse / Gesture 三个 Surface 共用，排序、过滤、匹配全部收敛在这里。
 */
class Resolver(
    private val store: IndexStore,
    private val pinyin: PinyinEngine,
    private val usage: UsageStore,
    private val context: ContextProvider,
    private val gestures: GestureRegistry = GestureRegistry(),
    private val pins: PinStore = PinStore.NONE,
    private val ranker: Ranker = Ranker(),
    private val dnd: TagDndRegistry = TagDndRegistry(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun resolve(query: Query): List<ScoredTarget> {
        val now = clock()

        query.gestureId?.let { return resolveGesture(it, now) }

        val candidates: List<Pair<IndexedTarget, MatchQuality?>> = when {
            query.tag != null && query.tag.name == TagResolver.ALL_APPS ->
                // Browse 的"全部"：全量，排序在下方按拼音走（配合首字母索引条）
                store.all().mapNotNull { store.entry(it.id)?.let { e -> e to null } }.toList()

            query.tag != null ->
                store.all()
                    .filter { query.tag in it.tags }
                    .mapNotNull { store.entry(it.id)?.let { e -> e to null } }
                    .toList()

            !query.text.isNullOrBlank() -> textCandidates(query.text)

            else -> // 空查询：Canvas 的 top-N，纯 frecency
                store.all().mapNotNull { store.entry(it.id)?.let { e -> e to null } }.toList()
        }

        val visibleCandidates = candidates.filter { dnd.isVisible(it.first.target, now) }

        if (visibleCandidates.isEmpty()) {
            // P1-6 无结果兜底：不道歉，直接给下一步
            if (!query.text.isNullOrBlank()) return listOf(webFallback(query.text.trim()))
            return emptyList()
        }

        // "全部"按拼音全拼排序（排序只能在引擎层，UI 拿到即是终态）
        if (query.tag != null && query.tag.name == TagResolver.ALL_APPS) {
            return visibleCandidates
                .sortedBy { it.first.keys.full }
                .map { (entry, _) -> withUsageReason(ranker.score(entry.target, null, 0f, 0f, 0f), entry.target.id, now) }
                .take(query.limit)
        }

        // frecency 在候选集内归一化：最高者 = 1
        val rawFrecency = visibleCandidates.map { frecencyRaw(it.first.target.id, now) }
        val maxRaw = rawFrecency.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val signals = context.current()

        return visibleCandidates.mapIndexed { i, (entry, quality) ->
            val id = entry.target.id
            val scored = ranker.score(
                target = entry.target,
                quality = quality,
                frecency = rawFrecency[i] / maxRaw,
                context = contextScore(id, signals, now),
                pin = if (pins.isPinned(id)) 1f else 0f,
            )
            if (quality == null) withUsageReason(scored, id, now) else scored
        }.sortedWith(
            compareByDescending<ScoredTarget> { it.score }
                .thenByDescending { it.breakdown.match }
                .thenByDescending { rawFrecencyOf(it.target.id, now) }
        ).take(query.limit)
    }

    /** 用户点开条目：记账 frecency，同时记下当时的设备信号（P1-5）。真正的 Intent 派发属平台层。 */
    fun launch(target: Target) {
        usage.record(target.id, clock(), context.current().signalMask())
    }

    private fun resolveGesture(gestureId: String, now: Long): List<ScoredTarget> {
        val targetId = gestures.targetFor(gestureId) ?: return emptyList()
        val entry = store.entry(targetId) ?: return emptyList()
        if (!dnd.isVisible(entry.target, now)) return emptyList()
        val scored = ranker.score(
            target = entry.target,
            quality = null,
            frecency = 0f,
            context = 0f,
            pin = 0f,
        )
        return listOf(withUsageReason(scored, targetId, now))
    }

    private fun textCandidates(text: String): List<Pair<IndexedTarget, MatchQuality?>> {
        val q = text.trim()
        return store.lookup(q)
            .mapNotNull { target ->
                val entry = store.entry(target.id) ?: return@mapNotNull null
                val quality = pinyin.match(q, entry.keys) ?: aliasMatch(q, entry.target.aliases)
                quality?.let { entry to it }
            }
            .toList()
    }

    private fun aliasMatch(q: String, aliases: List<String>): MatchQuality? {
        val lower = q.lowercase()
        for (alias in aliases) {
            val a = alias.lowercase()
            when {
                a == lower -> return MatchQuality.EXACT
                a.startsWith(lower) -> return MatchQuality.PREFIX
                a.contains(lower) -> return MatchQuality.SUBSTRING
            }
        }
        return null
    }

    /** frecency = count / (1 + daysSinceLastUse)，30 天窗口。 */
    private fun frecencyRaw(id: String, now: Long): Float {
        val count = usage.count(id, now)
        if (count == 0) return 0f
        val last = usage.lastUsedMillis(id) ?: return 0f
        val days = (now - last).coerceAtLeast(0L) / DAY_MILLIS.toFloat()
        return count / (1f + days)
    }

    private fun rawFrecencyOf(id: String, now: Long): Float = frecencyRaw(id, now)

    /**
     * context 得分：时段亲和度与当前活跃设备信号的亲和度取平均。
     * 信号读不到（null）则不参与，退化为 P0 的纯时段。
     */
    private fun contextScore(id: String, signals: ContextSignals, now: Long): Float {
        val total = usage.count(id, now)
        if (total == 0) return 0f
        val parts = mutableListOf(usage.countInSlot(id, signals.slot, now).toFloat() / total)
        for (mask in signals.activeDeviceMasks()) {
            parts += usage.countWithSignal(id, mask, now).toFloat() / total
        }
        return parts.average().toFloat()
    }

    /** 非文本查询的匹配原因："今天 N 次" / "近 30 天 N 次"。 */
    private fun withUsageReason(scored: ScoredTarget, id: String, now: Long): ScoredTarget {
        val count = usage.count(id, now)
        if (count == 0) return scored
        val last = usage.lastUsedMillis(id) ?: return scored
        val display = if (now - last < DAY_MILLIS) "今天 $count 次" else "近 30 天 $count 次"
        return scored.copy(reason = MatchReason(display))
    }

    /** P1-6：无命中时的兜底条目。launch.uri 是完整搜索 URL，平台层用 ACTION_VIEW 打开。 */
    private fun webFallback(text: String): ScoredTarget {
        val url = "https://www.bing.com/search?q=" +
            java.net.URLEncoder.encode(text, Charsets.UTF_8.name())
        val target = Target(
            id = "web://search/$text",
            kind = Kind.WEB,
            label = "在浏览器中搜索 $text",
            launch = LaunchSpec(url),
        )
        return ScoredTarget(target, 0f, MatchReason("网页搜索"), ScoreBreakdown(0f, 0f, 0f, 0f))
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
