package com.rubyketang.launcher.engine.tag

import com.rubyketang.launcher.model.Tag

/**
 * 05-product-spec.md §3.2.1 分类生成，六层来源：
 * 1. 用户覆盖（持久化，永远最高；由上层恢复进 [overrides]）——现在是一整个分类集合，不再是单值，
 *    支撑 §3.2.2 的多归属
 * 2. 内置 metadata 映射表（按包名前缀，随版本更新）
 * 3. `ApplicationInfo.category`（以 int 传入，engine 不依赖 Android）
 * 4. 名称关键词规则（"阅读/读书/小说" → 阅读；"银行/支付" → 金融）
 * 5. 行为聚类建议（见 [BehaviorClusterHints]，只在前四层都没命中时采用）
 * 6. 兜底"未分类"
 *
 * §3.2.2/§3.2.3：自动分类只给一个主分类，多分类由用户在长按菜单里多选勾选追加；
 * 一旦用户对某条目做过任何勾选改动，该条目就整体交给 [overrides]，不再跟自动层混合计算——
 * 这样用户取消勾选自动给的主分类后不会被自动层"纠正"回来。
 * 自定义分类允许创建，上限 8 个（§3.2.3）。
 */
class TagResolver(
    private val overrides: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    private val customCategories: MutableList<String> = mutableListOf(),
) {
    private var clusterHints: BehaviorClusterHints = BehaviorClusterHints.NONE

    /** 引擎层内部装配用：LauncherState 在每次重建索引前灌入最新的行为聚类模型。 */
    fun installClusterHints(hints: BehaviorClusterHints) {
        clusterHints = hints
    }

    fun resolve(targetId: String, label: String, packageName: String, androidCategory: Int): Set<Tag> {
        overrides[targetId]?.let { return it.map(::Tag).toSet() }
        return setOf(Tag(autoResolve(targetId, label, packageName, androidCategory)))
    }

    private fun autoResolve(targetId: String, label: String, packageName: String, androidCategory: Int): String =
        fromMetadata(packageName)
            ?: fromAndroidCategory(androidCategory)
            ?: fromKeyword(label)
            ?: clusterHints.hintFor(targetId)?.takeIf { it in ALL }
            ?: FALLBACK

    /** §3.2.3 单条编辑：长按 → 多选列表里勾/取消勾一个分类，立即生效，无需确认。 */
    fun toggleTag(targetId: String, currentTags: Set<String>, category: String) {
        require(category in allCategories()) { "未知分类: $category" }
        val next = currentTags.toMutableSet()
        if (!next.remove(category)) next.add(category)
        overrides[targetId] = next.ifEmpty { mutableSetOf(FALLBACK) }
    }

    /** §3.2.3 批量：底部"添加到分类"，对一批条目做同一个添加操作。 */
    fun addTag(targetId: String, currentTags: Set<String>, category: String) {
        require(category in allCategories()) { "未知分类: $category" }
        overrides[targetId] = (currentTags + category).toMutableSet()
    }

    /** §3.2.3 批量："移出分类"；移出后一个分类都不剩时回落"未分类"。 */
    fun removeTag(targetId: String, currentTags: Set<String>, category: String) {
        val next = (currentTags - category)
        overrides[targetId] = next.ifEmpty { setOf(FALLBACK) }.toMutableSet()
    }

    fun overrides(): Map<String, Set<String>> = overrides.mapValues { it.value.toSet() }

    fun restore(saved: Map<String, Set<String>>) {
        overrides.clear()
        saved.forEach { (id, tags) -> overrides[id] = tags.toMutableSet() }
    }

    /** §3.2.3 自定义分类：允许创建，上限 8 个；不能与固定表或已有自定义分类重名。 */
    fun addCustomCategory(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "分类名不能为空" }
        require(trimmed !in allCategories()) { "分类已存在: $trimmed" }
        require(customCategories.size < MAX_CUSTOM_CATEGORIES) { "自定义分类最多 $MAX_CUSTOM_CATEGORIES 个" }
        customCategories.add(trimmed)
    }

    /** 成员数为 0 的自定义分类允许删除腾出名额；调用方（LauncherState）负责数出当前成员数。 */
    fun removeCustomCategory(name: String, memberCount: Int) {
        require(memberCount == 0) { "分类下还有 $memberCount 个条目，不能删除" }
        customCategories.remove(name)
    }

    fun customCategories(): List<String> = customCategories.toList()

    fun restoreCustomCategories(saved: List<String>) {
        customCategories.clear()
        customCategories.addAll(saved.take(MAX_CUSTOM_CATEGORIES))
    }

    /** 固定表 + 自定义分类，供多选列表渲染和校验用。不含"未分类"——那是自动兜底状态，不是可勾选项。 */
    fun allCategories(): List<String> = ALL + customCategories

    /**
     * 05-product-spec.md §3.2.3：Browse 左栏只显示"有内容"的分类，固定表在前、自定义分类在后
     * （顺序沿用 [allCategories]）；自定义分类成员数 < [MIN_CUSTOM_CATEGORY_MEMBERS] 时不显示，
     * 只在编辑列表（[allCategories]）里可见——防止左栏长出一堆单成员分类。不含"全部"这个虚拟分类，
     * 那是调用方（Browse）自己在结果末尾拼接的，不属于 tag 体系。
     *
     * @param counts 每个分类名当前有多少条目命中（调用方按可见性/免打扰过滤后统计）
     */
    fun visibleCategories(counts: Map<String, Int>): List<String> {
        val custom = customCategories.toSet()
        return allCategories().filter { name ->
            val count = counts[name] ?: 0
            if (name in custom) count >= MIN_CUSTOM_CATEGORY_MEMBERS else count > 0
        }
    }

    companion object {
        const val ALL_APPS = "全部" // Browse 左栏底部的虚拟分类，不占条目 tag
        const val FALLBACK = "未分类"
        const val MAX_CUSTOM_CATEGORIES = 8
        const val MIN_CUSTOM_CATEGORY_MEMBERS = 3

        /** 固定分类表。用户不可增删改；自定义分类另见 [customCategories]。 */
        val ALL = listOf("社交", "阅读", "影音", "游戏", "图像", "资讯", "出行", "购物", "金融", "效率", "工具")

        // ApplicationInfo.category 的常量值（android.content.pm.ApplicationInfo）
        private const val CATEGORY_GAME = 0
        private const val CATEGORY_AUDIO = 1
        private const val CATEGORY_VIDEO = 2
        private const val CATEGORY_IMAGE = 3
        private const val CATEGORY_SOCIAL = 4
        private const val CATEGORY_NEWS = 5
        private const val CATEGORY_MAPS = 6
        private const val CATEGORY_PRODUCTIVITY = 7
        private const val CATEGORY_ACCESSIBILITY = 8

        private fun fromAndroidCategory(category: Int): String? = when (category) {
            CATEGORY_GAME -> "游戏"
            CATEGORY_AUDIO, CATEGORY_VIDEO -> "影音"
            CATEGORY_IMAGE -> "图像"
            CATEGORY_SOCIAL -> "社交"
            CATEGORY_NEWS -> "资讯"
            CATEGORY_MAPS -> "出行"
            CATEGORY_PRODUCTIVITY -> "效率"
            CATEGORY_ACCESSIBILITY -> "工具"
            else -> null // CATEGORY_UNDEFINED = -1
        }

        /** 内置 metadata 映射表：包名前缀 → 分类。国内 top app 人工维护，随版本更新。 */
        private val METADATA = listOf(
            "com.tencent.mm" to "社交",
            "com.tencent.weread" to "阅读",
            "com.autonavi" to "出行",
            "com.amap" to "出行",
            "com.taobao.taobao" to "购物",
            "com.eg.android.AlipayGphone" to "工具",
            "com.netease.cloudmusic" to "影音",
            "tv.danmaku.bili" to "影音",
            "com.ss.android.ugc" to "影音",
        )

        private fun fromMetadata(packageName: String): String? =
            METADATA.firstOrNull { (prefix, _) -> packageName.startsWith(prefix) }?.second

        /** 名称关键词规则：包名映射表和系统 category 都没命中时，按 app 显示名兜一层。 */
        private val KEYWORDS = listOf(
            listOf("阅读", "读书", "小说", "书城", "漫画") to "阅读",
            listOf("银行", "支付", "钱包", "信用卡", "理财", "证券", "基金") to "金融",
            listOf("地图", "导航", "打车", "出行", "公交", "铁路", "航班") to "出行",
            listOf("外卖", "商城", "购物", "超市", "电商") to "购物",
            listOf("视频", "影院", "追剧", "直播") to "影音",
            listOf("新闻", "资讯", "头条") to "资讯",
            listOf("相机", "修图", "相册", "美颜") to "图像",
            listOf("游戏", "手游") to "游戏",
        )

        private fun fromKeyword(label: String): String? {
            if (label.isEmpty()) return null
            return KEYWORDS.firstOrNull { (keywords, _) -> keywords.any { label.contains(it) } }?.second
        }
    }
}
