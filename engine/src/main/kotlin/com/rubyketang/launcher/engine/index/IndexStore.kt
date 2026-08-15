package com.rubyketang.launcher.engine.index

import com.rubyketang.launcher.engine.pinyin.PinyinEngine
import com.rubyketang.launcher.engine.pinyin.PinyinKeys
import com.rubyketang.launcher.model.Target
import com.rubyketang.launcher.source.TargetSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class IndexVersion(val version: Long, val size: Int)

/** Target 连同其预计算拼音键。 */
class IndexedTarget(val target: Target, val keys: PinyinKeys)

/**
 * P0-2 索引。内存倒排 + 前缀 Trie，不用 SQLite / Room。
 * 200–500 条目全内存；持久化快照（Proto DataStore）属 P0-10，不在本模块。
 */
interface IndexStore {
    fun lookup(prefix: String): Sequence<Target>
    fun all(): Sequence<Target>
    fun entry(id: String): IndexedTarget?
    suspend fun rebuild()
    fun observe(): StateFlow<IndexVersion>
}

class InMemoryIndexStore(
    private val sources: List<TargetSource>,
    private val pinyin: PinyinEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : IndexStore {

    private val versionFlow = MutableStateFlow(IndexVersion(version = 0, size = 0))

    @Volatile
    private var entries: Map<String, IndexedTarget> = emptyMap()

    @Volatile
    private var trie: PrefixTrie = PrefixTrie()

    /**
     * 取候选。Trie 命中时只返回命中条目；
     * 未命中时返回全量候选——"混合首字母"（如 wxdsh）和原文子串（如 读书）
     * 天然不是任何索引键的前缀，交由 PinyinEngine.match 精滤。
     * 500 条目的全量精滤仍在 8ms 预算内（见性能测试）。
     */
    override fun lookup(prefix: String): Sequence<Target> {
        val q = prefix.trim().lowercase()
        if (q.isEmpty()) return all()
        val map = entries
        val ids = trie.search(q)
        val candidates = if (ids.isEmpty()) map.keys else ids
        return candidates.asSequence().mapNotNull { map[it]?.target }
    }

    override fun all(): Sequence<Target> = entries.values.asSequence().map { it.target }

    override fun entry(id: String): IndexedTarget? = entries[id]

    override suspend fun rebuild() = withContext(dispatcher) {
        val loaded = sources.flatMap { it.load() }
        val newEntries = LinkedHashMap<String, IndexedTarget>(loaded.size)
        val newTrie = PrefixTrie()
        for (target in loaded) {
            val keys = pinyin.index(target.label)
            newEntries[target.id] = IndexedTarget(target, keys)
            insertKeys(newTrie, newEntries.getValue(target.id))
        }
        entries = newEntries
        trie = newTrie
        bumpVersion()
    }

    /** 增量更新（PACKAGE_ADDED / CHANGED），不做全量重建。 */
    suspend fun upsert(targets: Collection<Target>) = withContext(dispatcher) {
        val map = entries.toMutableMap()
        val t = trie
        for (target in targets) {
            map.remove(target.id)?.let { removeKeys(t, it) }
            val indexed = IndexedTarget(target, pinyin.index(target.label))
            map[target.id] = indexed
            insertKeys(t, indexed)
        }
        entries = map
        bumpVersion()
    }

    /** 增量更新（PACKAGE_REMOVED）。 */
    suspend fun remove(targetId: String) = withContext(dispatcher) {
        val map = entries.toMutableMap()
        map.remove(targetId)?.let { removeKeys(trie, it) }
        entries = map
        bumpVersion()
    }

    private fun insertKeys(trie: PrefixTrie, entry: IndexedTarget) {
        val keys = entry.keys
        val id = entry.target.id
        trie.insert(keys.lowerLabel, id)
        for (i in keys.syllables.indices) {
            trie.insert(keys.boundaryFulls[i], id)
            trie.insert(keys.boundaryInitials[i], id)
            trie.insert(keys.boundaryFuzzyFulls[i], id)
            trie.insert(keys.boundaryFuzzyInitials[i], id)
        }
        for (alias in entry.target.aliases) {
            trie.insert(alias.lowercase(), id)
        }
    }

    private fun removeKeys(trie: PrefixTrie, entry: IndexedTarget) {
        val keys = entry.keys
        val id = entry.target.id
        trie.remove(keys.lowerLabel, id)
        for (i in keys.syllables.indices) {
            trie.remove(keys.boundaryFulls[i], id)
            trie.remove(keys.boundaryInitials[i], id)
            trie.remove(keys.boundaryFuzzyFulls[i], id)
            trie.remove(keys.boundaryFuzzyInitials[i], id)
        }
        for (alias in entry.target.aliases) {
            trie.remove(alias.lowercase(), id)
        }
    }

    private fun bumpVersion() {
        versionFlow.value = IndexVersion(versionFlow.value.version + 1, entries.size)
    }

    override fun observe(): StateFlow<IndexVersion> = versionFlow
}
