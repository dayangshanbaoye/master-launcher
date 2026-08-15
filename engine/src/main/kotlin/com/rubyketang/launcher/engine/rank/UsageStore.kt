package com.rubyketang.launcher.engine.rank

/** 一条使用事件：时刻 + 当时的设备信号位掩码（时段可由时刻推出，不冗余存）。 */
data class UsageEvent(val atMillis: Long, val signalMask: Int = 0)

/**
 * frecency / context 的原始数据，30 天滑动窗口。
 * frecency = count / (1 + daysSinceLastUse)。
 */
interface UsageStore {
    fun record(id: String, atMillis: Long, signalMask: Int = 0)
    /** 30 天窗口内的使用次数。 */
    fun count(id: String, nowMillis: Long): Int
    /** 最近一次使用时刻；从未使用返回 null。 */
    fun lastUsedMillis(id: String): Long?
    /** 30 天窗口内、指定时段的使用次数（context 时段项）。 */
    fun countInSlot(id: String, slot: TimeSlot, nowMillis: Long): Int
    /** 30 天窗口内、携带指定信号位的使用次数（context 设备信号项，P1-5）。 */
    fun countWithSignal(id: String, signalMask: Int, nowMillis: Long): Int
    /** [fromMillis, toMillis) 区间内的使用次数（手势提议按天统计用，P1-3）。 */
    fun countInRange(id: String, fromMillis: Long, toMillis: Long): Int
    /** 所有有使用记录的 target id。 */
    fun ids(): Set<String>
}

class InMemoryUsageStore(
    private val windowMillis: Long = 30L * 24 * 60 * 60 * 1000,
    private val slotOf: (Long) -> TimeSlot = TimeSlot::of,
) : UsageStore {

    private val events = HashMap<String, MutableList<UsageEvent>>()

    override fun record(id: String, atMillis: Long, signalMask: Int) {
        events.getOrPut(id) { ArrayList() }.add(UsageEvent(atMillis, signalMask))
    }

    override fun count(id: String, nowMillis: Long): Int =
        events[id]?.count { nowMillis - it.atMillis < windowMillis } ?: 0

    override fun lastUsedMillis(id: String): Long? = events[id]?.maxOfOrNull { it.atMillis }

    override fun countInSlot(id: String, slot: TimeSlot, nowMillis: Long): Int =
        events[id]?.count { nowMillis - it.atMillis < windowMillis && slotOf(it.atMillis) == slot } ?: 0

    override fun countWithSignal(id: String, signalMask: Int, nowMillis: Long): Int =
        events[id]?.count { nowMillis - it.atMillis < windowMillis && it.signalMask and signalMask == signalMask } ?: 0

    override fun countInRange(id: String, fromMillis: Long, toMillis: Long): Int =
        events[id]?.count { it.atMillis in fromMillis until toMillis } ?: 0

    override fun ids(): Set<String> = events.keys

    /** 持久化快照（P0-10，Proto DataStore 由上层读写）。 */
    fun snapshot(): Map<String, List<UsageEvent>> = events.mapValues { it.value.toList() }

    fun restore(saved: Map<String, List<UsageEvent>>) {
        events.clear()
        saved.forEach { (id, list) -> events[id] = list.toMutableList() }
    }
}

/** pin 只表达"用户钉住了这条"，来源是条目长按菜单（UI 层的事）。 */
fun interface PinStore {
    fun isPinned(id: String): Boolean

    companion object {
        val NONE = PinStore { false }
    }
}
