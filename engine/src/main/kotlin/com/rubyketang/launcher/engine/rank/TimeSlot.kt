package com.rubyketang.launcher.engine.rank

import java.util.Calendar

/** 一天四个时段。P0 的 context 只用时段，其余信号见 ContextSignals 预留字段。 */
enum class TimeSlot {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun of(hour: Int): TimeSlot = when (hour) {
            in 6..11 -> MORNING
            in 12..17 -> AFTERNOON
            in 18..22 -> EVENING
            else -> NIGHT
        }

        fun of(epochMillis: Long): TimeSlot {
            val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
            return of(cal.get(Calendar.HOUR_OF_DAY))
        }
    }
}
