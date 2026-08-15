package com.rubyketang.launcher.engine.index

/** 前缀 Trie。key 上的每个节点都挂着"以该前缀开头的条目 id"。 */
internal class PrefixTrie {
    private class Node {
        val children = HashMap<Char, Node>()
        val ids = HashSet<String>()
    }

    private val root = Node()

    fun insert(key: String, id: String) {
        if (key.isEmpty()) return
        var node = root
        for (c in key) {
            node = node.children.getOrPut(c) { Node() }
            node.ids.add(id)
        }
    }

    /** 所有 key 以 [prefix] 开头的条目 id。 */
    fun search(prefix: String): Set<String> {
        var node = root
        for (c in prefix) {
            node = node.children[c] ?: return emptySet()
        }
        return node.ids
    }

    /** 增量更新用：把 id 从某条 key 的路径上摘掉。 */
    fun remove(key: String, id: String) {
        if (key.isEmpty()) return
        var node = root
        for (c in key) {
            node = node.children[c] ?: return
            node.ids.remove(id)
        }
    }
}
