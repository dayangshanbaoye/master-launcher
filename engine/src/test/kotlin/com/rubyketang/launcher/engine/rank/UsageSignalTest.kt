package com.rubyketang.launcher.engine.rank

import kotlin.test.Test
import kotlin.test.assertEquals

/** P1-5 完整 context 信号验收。 */
class UsageSignalTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `信号位掩码`() {
        val signals = ContextSignals(
            slot = TimeSlot.MORNING,
            headsetConnected = true,
            charging = false,
            carBluetoothConnected = true,
        )
        assertEquals(
            ContextSignals.MASK_HEADSET or ContextSignals.MASK_CAR_BLUETOOTH,
            signals.signalMask(),
        )
        assertEquals(
            listOf(ContextSignals.MASK_HEADSET, ContextSignals.MASK_CAR_BLUETOOTH),
            signals.activeDeviceMasks(),
        )
        // 读不到的信号（null）不计入
        assertEquals(0, ContextSignals(slot = TimeSlot.NIGHT).signalMask())
    }

    @Test
    fun `countWithSignal 只统计携带该信号的事件`() {
        val usage = InMemoryUsageStore(slotOf = { TimeSlot.MORNING })
        usage.record("a", now - 1000, ContextSignals.MASK_HEADSET)
        usage.record("a", now - 2000, ContextSignals.MASK_HEADSET or ContextSignals.MASK_CHARGING)
        usage.record("a", now - 3000, 0)

        assertEquals(3, usage.count("a", now))
        assertEquals(2, usage.countWithSignal("a", ContextSignals.MASK_HEADSET, now))
        assertEquals(1, usage.countWithSignal("a", ContextSignals.MASK_CHARGING, now))
        assertEquals(0, usage.countWithSignal("a", ContextSignals.MASK_CAR_BLUETOOTH, now))
        // 组合信号：事件必须同时带两位
        assertEquals(1, usage.countWithSignal("a", ContextSignals.MASK_HEADSET or ContextSignals.MASK_CHARGING, now))
    }

    @Test
    fun `countInRange 与 ids 供按天统计`() {
        val day = 24L * 60 * 60 * 1000
        val usage = InMemoryUsageStore(slotOf = { TimeSlot.MORNING })
        usage.record("a", now - day / 2)
        usage.record("a", now - day - day / 2)
        usage.record("b", now - day / 2)

        assertEquals(setOf("a", "b"), usage.ids())
        assertEquals(1, usage.countInRange("a", now - day, now))
        assertEquals(1, usage.countInRange("a", now - 2 * day, now - day))
        assertEquals(0, usage.countInRange("b", now - 2 * day, now - day))
    }

    @Test
    fun `快照往返保留信号位`() {
        val usage = InMemoryUsageStore(slotOf = { TimeSlot.MORNING })
        usage.record("a", now - 1000, ContextSignals.MASK_CHARGING)
        val restored = InMemoryUsageStore(slotOf = { TimeSlot.MORNING })
        restored.restore(usage.snapshot())
        assertEquals(1, restored.countWithSignal("a", ContextSignals.MASK_CHARGING, now))
    }
}
