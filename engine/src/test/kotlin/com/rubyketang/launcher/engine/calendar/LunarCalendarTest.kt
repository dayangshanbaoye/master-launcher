package com.rubyketang.launcher.engine.calendar

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 05-product-spec.md §2.2：农历计算走本地表，不联网。
 *
 * 覆盖范围收窄到 2000-01-01 ~ 2050-12-31（跟用户确认过，原 spec 写的 1900-2100 风险太高——
 * 200+ 年数据表全靠记忆转录，一个字节错了都可能没法自查出来）。
 *
 * 下面这批期望值不是凭记忆编的，是用 Python `lunarcalendar` 库对 2000-02-05~2050-12-31
 * 全部 18593 天做穷举比对、零差异之后，从中抽样导出的——包括平年、闰年、闰月边界（2012/2020/2025
 * 各自的闰四月/闰六月）、以及范围首尾边界。
 */
class LunarCalendarTest {

    private val cases = listOf(
        LocalDate.of(2000, 2, 5) to LunarDate(2000, 1, 1, false),
        LocalDate.of(2004, 1, 22) to LunarDate(2004, 1, 1, false),
        LocalDate.of(2004, 2, 20) to LunarDate(2004, 2, 1, false),
        LocalDate.of(2020, 4, 23) to LunarDate(2020, 4, 1, false),
        LocalDate.of(2020, 5, 23) to LunarDate(2020, 4, 1, true),
        LocalDate.of(2023, 1, 22) to LunarDate(2023, 1, 1, false),
        LocalDate.of(2023, 2, 1) to LunarDate(2023, 1, 11, false),
        LocalDate.of(2024, 2, 10) to LunarDate(2024, 1, 1, false),
        LocalDate.of(2024, 9, 3) to LunarDate(2024, 8, 1, false),
        LocalDate.of(2025, 1, 29) to LunarDate(2025, 1, 1, false),
        LocalDate.of(2025, 7, 25) to LunarDate(2025, 6, 1, true),
        LocalDate.of(2033, 3, 10) to LunarDate(2033, 2, 10, false),
        LocalDate.of(2033, 4, 10) to LunarDate(2033, 3, 11, false),
        LocalDate.of(2050, 1, 23) to LunarDate(2050, 1, 1, false),
        LocalDate.of(2050, 12, 31) to LunarDate(2050, 11, 18, false),
        LocalDate.of(2012, 4, 21) to LunarDate(2012, 4, 1, false),
        LocalDate.of(2012, 5, 21) to LunarDate(2012, 4, 1, true),
        LocalDate.of(2017, 6, 15) to LunarDate(2017, 5, 21, false),
        LocalDate.of(2017, 7, 15) to LunarDate(2017, 6, 22, false),
        LocalDate.of(2044, 1, 31) to LunarDate(2044, 1, 2, false),
    )

    @Test
    fun `权威样本逐一比对`() {
        cases.forEach { (solar, expected) ->
            assertEquals(expected, LunarCalendar.of(solar), "solar=$solar")
        }
    }

    @Test
    fun `覆盖范围之外返回 null 不崩溃`() {
        assertNull(LunarCalendar.of(LocalDate.of(1999, 12, 31)))
        // 注意：2051-01-01 阳历上跨年了，但农历新年通常在 1-2 月，此时仍属于表内的"农历 2050
        // 年"（阳历跨年不等于农历跨年），所以边界测试要用一个明确落在农历 2051 年的日期。
        assertNull(LunarCalendar.of(LocalDate.of(2051, 6, 1)))
    }

    @Test
    fun `display 格式为 月名+日名`() {
        assertEquals("正月初一", LunarDate(2024, 1, 1, false).display())
        assertEquals("六月十九", LunarDate(2024, 6, 19, false).display())
        assertEquals("闰四月初一", LunarDate(2020, 4, 1, true).display())
        assertEquals("腊月三十", LunarDate(2024, 12, 30, false).display())
    }
}
