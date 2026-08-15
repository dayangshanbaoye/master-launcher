package com.rubyketang.launcher.source

import com.rubyketang.launcher.model.Target

/**
 * 数据源适配器（P0-1 验收点）：新增一种数据源时只需实现本接口，
 * Resolver / Ranker / UI 全部不改一行。
 *
 * 实现方：AppSource(LauncherApps)、ShortcutSource(ShortcutManager)、
 * ContactSource、SettingSource …
 */
fun interface TargetSource {
    suspend fun load(): List<Target>
}
