package com.rubyketang.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import com.rubyketang.launcher.engine.tag.TagResolver
import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.Target
import com.rubyketang.launcher.source.TargetSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * P1-2 快捷方式进索引：索引 LauncherApps.getShortcuts()——
 * app 自己发布的静态/动态快捷方式（微信"扫一扫"、支付宝"付款码"）。
 * 作为默认 Launcher 天然有此权限，无需 QUERY_ALL_PACKAGES。
 */
class ShortcutSource(
    private val context: Context,
    private val tags: TagResolver,
    private val userAliases: () -> Map<String, List<String>>,
) : TargetSource {

    override suspend fun load(): List<Target> = withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = Process.myUserHandle()
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        )
        val shortcuts = try {
            launcherApps.getShortcuts(query, user)
        } catch (e: SecurityException) {
            null // 非默认 Launcher 时拿不到
        } ?: return@withContext emptyList()

        val appLabels = HashMap<String, String>()
        shortcuts.mapNotNull { it.toTarget(launcherApps, user, appLabels) }
    }

    /** 增量更新用：只重取单个包的快捷方式。 */
    suspend fun loadPackage(packageName: String): List<Target> = withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = Process.myUserHandle()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        val shortcuts = try {
            launcherApps.getShortcuts(query, user)
        } catch (e: SecurityException) {
            null
        } ?: return@withContext emptyList()
        val appLabels = HashMap<String, String>()
        shortcuts.mapNotNull { it.toTarget(launcherApps, user, appLabels) }
    }

    private fun ShortcutInfo.toTarget(
        launcherApps: LauncherApps,
        user: android.os.UserHandle,
        appLabels: MutableMap<String, String>,
    ): Target? {
        val shortLabel = shortLabel?.toString() ?: return null
        val pkg = `package`
        // 快捷方式与所属 app 复用同一枚系统图标，Browse 中每行都能稳定对齐。
        val launcherActivity = launcherApps.getActivityList(pkg, user).firstOrNull()
        val appLabel = appLabels.getOrPut(pkg) { launcherActivity?.label?.toString() ?: pkg }
        val id = "shortcut://$pkg/$id"
        return Target(
            id = id,
            kind = Kind.SHORTCUT,
            label = "$appLabel → $shortLabel",
            aliases = userAliases()[id] ?: emptyList(),
            tags = setOf(tags.resolve(id, pkg, -1)),
            iconUri = launcherActivity?.componentName?.let { "icon://$pkg/${it.className}" },
            launch = LaunchSpec(id),
        )
    }
}
