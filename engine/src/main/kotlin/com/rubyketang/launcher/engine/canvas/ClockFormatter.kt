package com.rubyketang.launcher.engine.canvas

import com.rubyketang.launcher.engine.calendar.LunarDate
import java.time.DayOfWeek
import java.time.LocalDate

data class ClockLines(val sublines: List<String>)

/**
 * 05-product-spec.md §2.2 时钟区排布：副元素数（0/1/2/3）决定排布，
 * 顺序固定"星期 · 日期 · 农历"，用户只能决定显示与否、不能调顺序。
 *
 * 星期名不用 `SimpleDateFormat("E", Locale.getDefault())`——那会跟着系统 locale 走，
 * 英文系统下显示"Sat"，对一个中文产品来说不一致，这里固定用中文。
 */
object ClockFormatter {
    fun weekdayOf(date: LocalDate): String = when (date.dayOfWeek!!) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }

    private fun dateOf(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

    fun format(
        date: LocalDate,
        showWeekday: Boolean,
        showDate: Boolean,
        showLunar: Boolean,
        lunar: LunarDate?,
    ): ClockLines {
        val elements = buildList {
            if (showWeekday) add(weekdayOf(date))
            if (showDate) add(dateOf(date))
            // 算不出来（超出覆盖范围）就当没开，不占副元素位置、不崩溃。
            if (showLunar && lunar != null) add(lunar.display())
        }
        val sublines = when (elements.size) {
            0 -> emptyList()
            1, 2 -> listOf(elements.joinToString(" · "))
            else -> listOf(elements.take(2).joinToString(" · "), elements[2])
        }
        return ClockLines(sublines)
    }
}
