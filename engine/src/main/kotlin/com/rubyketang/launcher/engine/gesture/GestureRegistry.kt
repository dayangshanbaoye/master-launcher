package com.rubyketang.launcher.engine.gesture

/**
 * 只存 gestureId → targetId 映射，零业务逻辑（P0-8 验收点）。
 */
class GestureRegistry {
    private val bindings = HashMap<String, String>()

    fun bind(gestureId: String, targetId: String) {
        bindings[gestureId] = targetId
    }

    fun unbind(gestureId: String) {
        bindings.remove(gestureId)
    }

    fun targetFor(gestureId: String): String? = bindings[gestureId]

    /** 持久化快照。 */
    fun bindings(): Map<String, String> = bindings.toMap()

    fun restore(saved: Map<String, String>) {
        bindings.clear()
        bindings.putAll(saved)
    }
}
