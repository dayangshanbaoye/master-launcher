package com.rubyketang.launcher.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.rubyketang.launcher.engine.tag.TagResolver
import com.rubyketang.launcher.model.Target
import com.rubyketang.launcher.source.TargetSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * P0-10 数据源：LauncherApps（不用 PackageManager.getInstalledApplications）。
 * 作为默认 Launcher 天然可见全部可启动 activity。
 */
class AppSource(
    private val context: Context,
    private val tags: TagResolver,
    private val userAliases: () -> Map<String, List<String>>,
) : TargetSource {

    private val mapper = LaunchableActivityMapper(tags, userAliases)

    override suspend fun load(): List<Target> = withContext(Dispatchers.IO) {
        val launcherApps = launcherApps()
        val users = launcherApps.profiles.ifEmpty { listOf(Process.myUserHandle()) }
        val primarySerial = serialOf(Process.myUserHandle())
        mapper.map(users.flatMap { user ->
            val profileSerial = serialOf(user).takeUnless { it == primarySerial }
            launcherApps.getActivityList(null, user).map { it.toLaunchableActivity(profileSerial) }
        })
    }

    /** 增量更新用：只重取单个包。 */
    suspend fun loadPackage(packageName: String, user: UserHandle = Process.myUserHandle()): List<Target> = withContext(Dispatchers.IO) {
        val profileSerial = serialOf(user).takeUnless { it == serialOf(Process.myUserHandle()) }
        mapper.map(launcherApps().getActivityList(packageName, user).map { it.toLaunchableActivity(profileSerial) })
    }

    private fun LauncherActivityInfo.toLaunchableActivity(profileSerial: Long?): LaunchableActivity = LaunchableActivity(
        packageName = applicationInfo.packageName,
        className = componentName.className,
        label = label.toString(),
        applicationCategory = applicationInfo.category,
        profileSerial = profileSerial,
    )

    private fun launcherApps(): LauncherApps =
        context.getSystemService(LauncherApps::class.java)

    private fun serialOf(user: UserHandle): Long =
        context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
}
