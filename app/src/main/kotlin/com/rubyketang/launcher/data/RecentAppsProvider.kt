package com.rubyketang.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context

/** Android 不向第三方 Launcher 暴露真实任务栈；这里提供用户授权后的最近使用应用。 */
class RecentAppsProvider(private val context: Context) {
    fun isAccessGranted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun packageNames(limit: Int): List<String> {
        if (!isAccessGranted()) return emptyList()
        val now = System.currentTimeMillis()
        val usage = context.getSystemService(UsageStatsManager::class.java)
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - LOOKBACK_MILLIS, now)
        return usage
            .asSequence()
            .filter { it.packageName != context.packageName && it.lastTimeUsed > 0L }
            .sortedByDescending { it.lastTimeUsed }
            .map { it.packageName }
            .distinct()
            .take(limit)
            .toList()
    }

    private companion object {
        const val LOOKBACK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
