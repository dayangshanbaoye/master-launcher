package com.rubyketang.launcher.engine.calendar

import java.time.LocalDate

/**
 * 农历日期。[month] 是 1..12，与 [isLeapMonth] 组合表示"闰几月"。
 */
data class LunarDate(val year: Int, val month: Int, val day: Int, val isLeapMonth: Boolean) {
    /** UI 展示用的传统写法，如"六月十九""闰四月初一""腊月三十"。 */
    fun display(): String {
        val monthName = if (isLeapMonth) "闰${MONTH_NAMES[month - 1]}" else MONTH_NAMES[month - 1]
        return monthName + DAY_NAMES[day - 1]
    }

    private companion object {
        val MONTH_NAMES = listOf("正月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "腊月")
        val DAY_NAMES = listOf(
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
        )
    }
}

/**
 * 05-product-spec.md §2.2："农历计算走本地表，不联网。"
 *
 * 覆盖 2000-01-01 ~ 2050-12-31（跟 spec 原写的 1900-2100 相比收窄了范围——见
 * LunarCalendarTest.kt 顶部注释，200+ 年数据表全靠记忆转录风险太高，这批数据改用
 * Python `lunarcalendar` 库穷举校验生成，不是手打）。超出范围返回 null，调用方兜底
 * 不显示农历行，不崩溃。
 *
 * 编码：`LUNAR_INFO[年份-2000]` 每个 Int 用 17 位——bit 0..11 对应农历 1..12 月是否为
 * 大月（30 天=1，29 天=0），bit 12..15 是闰月月份（0=无闰月），bit 16 是闰月是否为大月。
 */
object LunarCalendar {
    private const val EPOCH_YEAR = 2000
    private val EPOCH = LocalDate.of(2000, 2, 5) // 农历 2000 年正月初一

    fun of(date: LocalDate): LunarDate? {
        var offset = java.time.temporal.ChronoUnit.DAYS.between(EPOCH, date).toInt()
        if (offset < 0) return null
        var year = EPOCH_YEAR
        while (true) {
            val yearDays = yearTotalDays(year)
            if (offset < yearDays) break
            offset -= yearDays
            year += 1
            if (year - EPOCH_YEAR >= LUNAR_INFO.size) return null
        }
        val leapMonth = leapMonthOf(year)
        var month = 1
        var isLeap = false
        while (true) {
            val monthDays = if (isLeap) leapDays(year) else monthDays(year, month)
            if (offset < monthDays) break
            offset -= monthDays
            if (!isLeap && leapMonth == month) {
                isLeap = true
            } else {
                isLeap = false
                month += 1
            }
        }
        return LunarDate(year, month, offset + 1, isLeap)
    }

    private fun leapMonthOf(year: Int): Int = (LUNAR_INFO[year - EPOCH_YEAR] shr 12) and 0xF

    private fun leapDays(year: Int): Int {
        if (leapMonthOf(year) == 0) return 0
        return if ((LUNAR_INFO[year - EPOCH_YEAR] shr 16) and 0x1 == 1) 30 else 29
    }

    private fun monthDays(year: Int, month: Int): Int =
        if ((LUNAR_INFO[year - EPOCH_YEAR] shr (month - 1)) and 0x1 == 1) 30 else 29

    private fun yearTotalDays(year: Int): Int =
        (1..12).sumOf { monthDays(year, it) } + leapDays(year)

    /** 2000..2050，共 51 个条目；生成方式见类注释。 */
    private val LUNAR_INFO = intArrayOf(
        0x00693, 0x04A9B, 0x0052B, 0x00A5B, 0x02AAE, 0x0056A, 0x07DD5, 0x00BA4, 0x00B49, 0x05D53,
        0x00A95, 0x0052D, 0x0455D, 0x00AB5, 0x09BAA, 0x005D2, 0x00DA5, 0x16E8A, 0x00D4A, 0x00C95,
        0x04A9E, 0x00556, 0x00AB5, 0x02ADA, 0x006D2, 0x06765, 0x00725, 0x0064B, 0x05657, 0x00CAB,
        0x0055A, 0x0356E, 0x00B69, 0x0BF52, 0x00B52, 0x00B25, 0x16D0B, 0x00A4B, 0x004AB, 0x052BB,
        0x005AD, 0x00B6A, 0x02DAA, 0x00D92, 0x07EA5, 0x00D25, 0x00A55, 0x15A4D, 0x004B6, 0x005B5,
        0x136D2,
    )
}
