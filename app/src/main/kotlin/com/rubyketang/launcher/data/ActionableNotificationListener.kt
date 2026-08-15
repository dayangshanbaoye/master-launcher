package com.rubyketang.launcher.data

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import android.service.notification.NotificationListenerService
import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.Tag
import com.rubyketang.launcher.model.Target
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 只保留带 contentIntent 的通知；不读取正文或会话内容。 */
data class ActionableNotification(val target: Target, val pendingIntent: PendingIntent, val postedAt: Long)

object ActionableNotificationRegistry {
    private val records = linkedMapOf<String, ActionableNotification>()
    private val _entries = MutableStateFlow<List<ActionableNotification>>(emptyList())
    val entries: StateFlow<List<ActionableNotification>> = _entries.asStateFlow()

    fun post(context: Context, sbn: StatusBarNotification) {
        val pendingIntent = sbn.notification.contentIntent ?: return
        val packageLabel = try {
            context.packageManager.getApplicationLabel(
                @Suppress("DEPRECATION") context.packageManager.getApplicationInfo(sbn.packageName, 0),
            ).toString()
        } catch (_: Exception) {
            sbn.packageName.substringAfterLast('.')
        }
        val target = Target(
            id = "notification://${sbn.key}",
            kind = Kind.ACTION,
            label = "$packageLabel 的通知",
            aliases = listOf(packageLabel, "通知"),
            tags = setOf(Tag("通知")),
            launch = LaunchSpec("notification://${sbn.key}"),
        )
        records[sbn.key] = ActionableNotification(target, pendingIntent, sbn.postTime)
        publish()
    }

    fun remove(key: String) {
        records.remove(key)
        publish()
    }

    fun clear() {
        records.clear()
        publish()
    }

    fun replace(context: Context, notifications: Array<StatusBarNotification>) {
        records.clear()
        notifications.forEach { post(context, it) }
        publish()
    }

    fun send(key: String): Boolean = try {
        records[key]?.pendingIntent?.send()
        true
    } catch (_: Exception) {
        remove(key)
        false
    }

    private fun publish() {
        _entries.value = records.values.sortedByDescending { it.postedAt }
    }
}

/** P2-1 的最小权限边界：只索引可点击通知，Canvas 中只占用一个 top-4 位置。 */
class ActionableNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        ActionableNotificationRegistry.replace(this, activeNotifications ?: emptyArray())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        ActionableNotificationRegistry.post(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        ActionableNotificationRegistry.remove(sbn.key)
    }

    override fun onListenerDisconnected() {
        ActionableNotificationRegistry.clear()
        super.onListenerDisconnected()
    }
}
