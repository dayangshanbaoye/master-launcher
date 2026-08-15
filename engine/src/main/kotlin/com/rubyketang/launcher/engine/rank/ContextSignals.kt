package com.rubyketang.launcher.engine.rank

/**
 * Ranker 的 context 输入。
 * P0 只用 [slot]；P1-5 接入耳机 / 充电 / 车载蓝牙。
 * 信号读不到时为 null（权限缺失、硬件不支持），此时该项不参与计分。
 */
data class ContextSignals(
    val slot: TimeSlot,
    val headsetConnected: Boolean? = null,
    val charging: Boolean? = null,
    val carBluetoothConnected: Boolean? = null,
) {
    /** 当前活跃信号的位掩码，随 usage 事件一起记录。 */
    fun signalMask(): Int {
        var mask = 0
        if (headsetConnected == true) mask = mask or MASK_HEADSET
        if (charging == true) mask = mask or MASK_CHARGING
        if (carBluetoothConnected == true) mask = mask or MASK_CAR_BLUETOOTH
        return mask
    }

    /** 当前活跃信号的位列表（含时段以外的设备信号），计分用。 */
    fun activeDeviceMasks(): List<Int> {
        val masks = mutableListOf<Int>()
        if (headsetConnected == true) masks += MASK_HEADSET
        if (charging == true) masks += MASK_CHARGING
        if (carBluetoothConnected == true) masks += MASK_CAR_BLUETOOTH
        return masks
    }

    companion object {
        const val MASK_HEADSET = 1
        const val MASK_CHARGING = 2
        const val MASK_CAR_BLUETOOTH = 4
    }
}

fun interface ContextProvider {
    fun current(): ContextSignals
}

class SystemContextProvider(private val clock: () -> Long = System::currentTimeMillis) : ContextProvider {
    override fun current(): ContextSignals = ContextSignals(slot = TimeSlot.of(clock()))
}
