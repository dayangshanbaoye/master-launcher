package com.rubyketang.launcher.engine.tag

import com.rubyketang.launcher.model.Tag

/**
 * P0-9 分类生成。来源优先级：
 * 1. 用户对单条目的覆盖（持久化，由上层恢复进 [overrides]）
 * 2. ApplicationInfo.category（以 int 传入，engine 不依赖 Android）
 * 3. 内置 metadata 映射表（按包名前缀，随版本更新）
 * 4. 兜底 "其他"
 *
 * 用户不能创建/重命名/删除分类，只能覆盖单个条目的归属。
 */
class TagResolver(
    private val overrides: MutableMap<String, String> = mutableMapOf(),
) {
    fun resolve(targetId: String, packageName: String, androidCategory: Int): Tag {
        overrides[targetId]?.let { return Tag(it) }
        fromAndroidCategory(androidCategory)?.let { return Tag(it) }
        fromMetadata(packageName)?.let { return Tag(it) }
        return Tag(FALLBACK)
    }

    fun override(targetId: String, category: String) {
        require(category in ALL) { "未知分类: $category" }
        overrides[targetId] = category
    }

    fun overrides(): Map<String, String> = overrides.toMap()

    fun restore(saved: Map<String, String>) {
        overrides.clear()
        overrides.putAll(saved)
    }

    companion object {
        const val ALL_APPS = "全部" // Browse 左栏底部的虚拟分类，不占条目 tag
        const val FALLBACK = "其他"

        /** 全量分类表。用户不可增删改。 */
        val ALL = listOf("社交", "阅读", "影音", "游戏", "图像", "资讯", "出行", "购物", "效率", "工具", FALLBACK)

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

        /** 内置 metadata 映射表：包名前缀 → 分类。 */
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
    }
}
