package com.rubyketang.launcher.model

/** 可启动对象的类别。 */
enum class Kind { APP, SHORTCUT, CONTACT, SETTING, ACTION, WEB }

/** 分类标签。引擎生成，非用户维护（见 P0-9）。 */
data class Tag(val name: String)

/**
 * 启动信息。只存可序列化的描述，不存任何 Android 对象（Intent/PendingIntent）。
 * 平台层在真正启动时据此构造 Intent。
 */
data class LaunchSpec(val uri: String)

/**
 * P0-1 统一对象模型：所有可启动的东西（app、快捷方式、联系人、系统设置项）统一为 Target。
 *
 * - [id] 必须稳定：应用更新、重装后不变，否则 frecency 和手势绑定会丢。
 * - 不存 Drawable，图标只存 [iconUri]，走独立的 IconCache。
 */
data class Target(
    val id: String,
    val kind: Kind,
    val label: String,
    val aliases: List<String> = emptyList(),
    val tags: Set<Tag> = emptySet(),
    val iconUri: String? = null,
    val launch: LaunchSpec,
)
