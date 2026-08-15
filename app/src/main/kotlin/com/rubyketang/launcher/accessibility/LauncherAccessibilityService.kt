package com.rubyketang.launcher.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * 05-product-spec.md §1 手势系统的无障碍后盾：上滑最近任务、双指下滑通知栏/锁屏。
 *
 * 只用 [performGlobalAction]，不订阅任何有意义的事件、不读取窗口内容——
 * `onAccessibilityEvent` 是必须重写的抽象方法，这里始终空实现。
 */
class LauncherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        private var instance: LauncherAccessibilityService? = null

        /** §1.3：上滑最近任务。未授权（服务未连接）时静默不做任何事，不弹窗。 */
        fun openRecents(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false

        /** §1.2：双指下滑通知栏。 */
        fun expandNotifications(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        /** §1.2 可选改绑：双指下滑锁屏。`GLOBAL_ACTION_LOCK_SCREEN` 需要 API 28+。 */
        fun lockScreen(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }
    }
}

/**
 * 05-product-spec.md §4.5 无障碍失效自检：开机 + 每次回到 Canvas 检查一次，
 * 不依赖服务实例是否已连接（那只反映"这个进程见过它"），而是查系统真实的已启用列表，
 * 国产 ROM 后台清理静默关闭服务时也能测出来。
 */
object AccessibilityStatus {
    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val target = ComponentName(context, LauncherAccessibilityService::class.java)
        return manager
            .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo.serviceInfo
                service.packageName == target.packageName && service.name == target.className
            }
    }

    fun openSettings(context: Context) {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
