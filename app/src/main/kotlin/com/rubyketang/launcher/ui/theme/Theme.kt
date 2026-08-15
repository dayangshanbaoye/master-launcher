package com.rubyketang.launcher.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 04-design-system.md 的 design tokens。 */
object WarmColors {
    val BgDark = Color(0xFF14100E)
    val BgLight = Color(0xFFFAF6F0)
    val FgDark = Color(0xFFEDE6DC)
    val FgLight = Color(0xFF26221E)
    val Fg2Dark = Color(0xFF8C837A)
    val Fg2Light = Color(0xFF6E665E)
    val LineDark = Color(0xFF2A2320)
    val LineLight = Color(0xFFE5DDD3)
    val Accent = Color(0xFFC97A40)
}

data class Palette(
    val bg: Color,
    val fg: Color,
    val fg2: Color,
    val line: Color,
    val accent: Color,
)

fun warmPalette(dark: Boolean): Palette = if (dark) {
    Palette(WarmColors.BgDark, WarmColors.FgDark, WarmColors.Fg2Dark, WarmColors.LineDark, WarmColors.Accent)
} else {
    Palette(WarmColors.BgLight, WarmColors.FgLight, WarmColors.Fg2Light, WarmColors.LineLight, WarmColors.Accent)
}

/**
 * 字号原本只有三级；05-product-spec.md §2.2 给时钟区加了按副元素数量变化的排布，
 * 引入 ClockLarge/ClockWithSub/ClockSubline 三个新尺寸，新 spec 优先于旧的"只有三级"。
 */
object Type {
    val Item: androidx.compose.ui.unit.TextUnit
        @Composable get() = 13.sp * LocalUiScale.current
    val Secondary: androidx.compose.ui.unit.TextUnit
        @Composable get() = 11.sp * LocalUiScale.current
    /** §2.2：0 个副元素时，时间单独 48sp。 */
    val ClockLarge: androidx.compose.ui.unit.TextUnit
        @Composable get() = 48.sp * LocalUiScale.current
    /** §2.2：1-3 个副元素时，时间 32sp。 */
    val ClockWithSub: androidx.compose.ui.unit.TextUnit
        @Composable get() = 32.sp * LocalUiScale.current
    /** §2.2：1-2 个副元素合并成一行时，副行 12sp（3 个副元素拆两行时用 Secondary 11sp）。 */
    val ClockSubline: androidx.compose.ui.unit.TextUnit
        @Composable get() = 12.sp * LocalUiScale.current
}

val LocalUiScale = staticCompositionLocalOf { 1f }

object Dimens {
    val ScreenMargin = 20.dp
    val SearchRowHeight = 56.dp
    val BrowseRowHeight = 44.dp
    val BrowseRowSpacing = 7.dp
    val BrowseIconSize = 20.dp
    val BrowseIconGap = 10.dp
    val BrowseIndexGap = 8.dp
    val BrowseIndexWidth = 28.dp
    val BrowseIndexTapHeight = 20.dp
    val RailWidth = 76.dp
    val CornerRadius = 8.dp
    val SheetRadius = 16.dp
    val Border = 0.5.dp
    val BorderSelected = 1.dp
    val GestureThreshold = 48.dp
    val TwoFingerMinDistance = 60.dp
}

/** Sheet 与页面动效统一用这个 spring。 */
fun <T> motionSpec() = spring<T>(dampingRatio = 0.85f, stiffness = 400f)
