package com.rubyketang.launcher.engine.canvas

import com.rubyketang.launcher.engine.calendar.LunarDate
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 05-product-spec.md §2.2：副元素数决定排布，顺序固定"星期·日期·农历"，用户只能决定显示与否。
 * 2024-06-19 是周三，用来核对星期计算。
 */
class ClockFormatterTest {

    private val wed = LocalDate.of(2024, 6, 19)
    private val lunar = LunarDate(2024, 5, 14, false) // 随便取一个，只测格式不测农历算法本身

    @Test
    fun `零副元素 只有时间 无副行`() {
        val result = ClockFormatter.format(wed, showWeekday = false, showDate = false, showLunar = false, lunar = null)
        assertEquals("周三", ClockFormatter.weekdayOf(wed)) // 顺带核对星期计算不依赖系统 locale
        assertEquals(emptyList(), result.sublines)
    }

    @Test
    fun `一个副元素 星期 单独一行`() {
        val result = ClockFormatter.format(wed, showWeekday = true, showDate = false, showLunar = false, lunar = null)
        assertEquals(listOf("周三"), result.sublines)
    }

    @Test
    fun `一个副元素 农历 单独一行`() {
        val result = ClockFormatter.format(wed, showWeekday = false, showDate = false, showLunar = true, lunar = lunar)
        assertEquals(listOf(lunar.display()), result.sublines)
    }

    @Test
    fun `两个副元素 星期和日期 用点号连接成一行`() {
        val result = ClockFormatter.format(wed, showWeekday = true, showDate = true, showLunar = false, lunar = null)
        assertEquals(listOf("周三 · 6月19日"), result.sublines)
    }

    @Test
    fun `两个副元素 星期和农历 仍按星期在前的固定顺序连接`() {
        val result = ClockFormatter.format(wed, showWeekday = true, showDate = false, showLunar = true, lunar = lunar)
        assertEquals(listOf("周三 · ${lunar.display()}"), result.sublines)
    }

    @Test
    fun `三个副元素 拆成两行 第一行星期点日期 第二行农历`() {
        val result = ClockFormatter.format(wed, showWeekday = true, showDate = true, showLunar = true, lunar = lunar)
        assertEquals(listOf("周三 · 6月19日", lunar.display()), result.sublines)
    }

    @Test
    fun `农历开启但当天算不出来时 当成未开启处理`() {
        // 覆盖范围外或算法异常时 lunar 传 null，不应该占用一个副元素位置或崩溃
        val result = ClockFormatter.format(wed, showWeekday = true, showDate = true, showLunar = true, lunar = null)
        assertEquals(listOf("周三 · 6月19日"), result.sublines)
    }
}
