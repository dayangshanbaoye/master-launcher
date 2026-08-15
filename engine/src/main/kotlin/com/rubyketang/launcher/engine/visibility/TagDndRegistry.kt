package com.rubyketang.launcher.engine.visibility

import com.rubyketang.launcher.model.Tag
import com.rubyketang.launcher.model.Target

/**
 * P2-2 临时免打扰的纯 JVM 状态。
 *
 * 这里的“隐藏”只影响 Resolver 给 Surface 的结果，不会卸载应用、删除索引或破坏手势绑定。
 * 到期项在读取时惰性清理，因此进程重启和设备时钟前进后都会自然恢复。
 */
class TagDndRegistry {
    private val hiddenUntil = linkedMapOf<String, Long>()

    fun hide(tag: Tag, untilMillis: Long) {
        hiddenUntil[tag.name] = untilMillis
    }

    fun restore(tag: Tag) {
        hiddenUntil.remove(tag.name)
    }

    fun isHidden(tag: Tag, nowMillis: Long): Boolean {
        val until = hiddenUntil[tag.name] ?: return false
        if (until <= nowMillis) {
            hiddenUntil.remove(tag.name)
            return false
        }
        return true
    }

    fun isVisible(target: Target, nowMillis: Long): Boolean =
        target.tags.none { isHidden(it, nowMillis) }

    fun snapshot(nowMillis: Long): Map<String, Long> {
        hiddenUntil.entries.removeAll { it.value <= nowMillis }
        return hiddenUntil.toMap()
    }

    fun restore(snapshot: Map<String, Long>) {
        hiddenUntil.clear()
        hiddenUntil.putAll(snapshot)
    }
}
