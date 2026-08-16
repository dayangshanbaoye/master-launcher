package com.rubyketang.launcher.data

import com.rubyketang.launcher.engine.tag.TagResolver
import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.Target

/** Android API 的原始活动信息在 Source 边界转换为可 JVM 验证的数据。 */
data class LaunchableActivity(
    val packageName: String,
    val className: String,
    val label: String,
    val applicationCategory: Int,
    /** null 表示当前主用户；非空值是 Android work/clone profile 的稳定 serial。 */
    val profileSerial: Long? = null,
)

/**
 * 不做分页、数量截断或按名称去重：每一个可启动 Activity 都是一个稳定 Target。
 * 同一应用有多个 launcher activity 时也必须保留，才能直达对应入口。
 */
class LaunchableActivityMapper(
    private val tags: TagResolver,
    private val userAliases: () -> Map<String, List<String>>,
) {
    fun map(activities: Iterable<LaunchableActivity>): List<Target> = activities.map { activity ->
        val profileSuffix = activity.profileSerial?.let { "?profile=$it" }.orEmpty()
        val id = "app://${activity.packageName}/${activity.className}$profileSuffix"
        Target(
            id = id,
            kind = Kind.APP,
            label = activity.label,
            aliases = userAliases()[id] ?: emptyList(),
            tags = tags.resolve(id, activity.label, activity.packageName, activity.applicationCategory),
            iconUri = "icon://${activity.packageName}/${activity.className}$profileSuffix",
            launch = LaunchSpec(id),
        )
    }
}
