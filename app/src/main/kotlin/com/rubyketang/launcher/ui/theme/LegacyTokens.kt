package com.rubyketang.launcher.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 桥接层：`CanvasScreen`/`SearchScreen`/`BrowseScreen` 等一批 Composable 是按
 * `palette: Palette` 参数传递 + `Type.*`/`Dimens.*` 取值的老写法写的（05spec 之前的
 * Warm Minimal 主题）。Clay Orange 换成了 `LocalColors`/`LauncherType`/`Dim` 这套新
 * CompositionLocal API（见 Theme.kt），但把 180 处调用点逐个迁到新 API 属于纯视觉换肤
 * 之外的结构性改动，风险和这次的诉求（配色/字体/间距对齐新设计稿）不成比例。
 *
 * 这里把旧符号保留下来，值全部改成从 Clay 色板/字号/度量派生——所有 Surface 不改一行
 * 代码就换上新配色。等 Surface 层真要做结构性调整时，再顺手把调用点迁到 `LocalColors.current`
 * / `LauncherType` / `Dim`，到时候删掉这个文件即可。
 */

/** Material 之外，旧调用点仍按这五个角色取色。 */
data class Palette(
    val bg: Color,
    val fg: Color,
    val fg2: Color,
    val line: Color,
    val accent: Color,
)

fun warmPalette(dark: Boolean): Palette = if (dark) {
    Palette(ClayDark.bg, ClayDark.text, ClayDark.text2, ClayDark.line, ClayDark.clay)
} else {
    Palette(ClayLight.bg, ClayLight.text, ClayLight.text2, ClayLight.line, ClayLight.clay)
}

/** 与 [LocalColors] 同步——在 Composable 里想要 Clay 专属角色（raised/sunk/text3/…）时优先用它。 */
fun LauncherColors.asPalette(): Palette = Palette(bg = bg, fg = text, fg2 = text2, line = line, accent = clay)

val LocalUiScale = staticCompositionLocalOf { 1f }

/**
 * 字号原本只有三级；05-product-spec.md §2.2 给时钟区加了按副元素数量变化的排布，
 * 引入 ClockLarge/ClockWithSub/ClockSubline 三个新尺寸。数值现在对齐 style-guide.html
 * 的 clock-xl(44sp) / clock(32sp) / row(13.5sp) / meta(11.5sp)。
 */
object Type {
    val Item: androidx.compose.ui.unit.TextUnit
        @Composable get() = LauncherType.row.fontSize * LocalUiScale.current
    val Secondary: androidx.compose.ui.unit.TextUnit
        @Composable get() = LauncherType.meta.fontSize * LocalUiScale.current
    /** §2.2：0 个副元素时，时间单独，对齐 style-guide clock-xl。 */
    val ClockLarge: androidx.compose.ui.unit.TextUnit
        @Composable get() = LauncherType.clockXl.fontSize * LocalUiScale.current
    /** §2.2：1-3 个副元素时，时间收窄，对齐 style-guide clock。 */
    val ClockWithSub: androidx.compose.ui.unit.TextUnit
        @Composable get() = LauncherType.clock.fontSize * LocalUiScale.current
    /** §2.2：1-2 个副元素合并成一行时，副行字号（3 个副元素拆两行时用 Secondary）。 */
    val ClockSubline: androidx.compose.ui.unit.TextUnit
        @Composable get() = 12.sp * LocalUiScale.current
}

object Dimens {
    val ScreenMargin = Dim.screenPad
    val SearchRowHeight = 56.dp
    val BrowseRowHeight = Dim.browseRow
    val BrowseRowSpacing = 6.dp
    val BrowseIconSize = Dim.iconSize
    val BrowseIconGap = Dim.iconGap
    val BrowseIndexGap = 8.dp
    val BrowseIndexWidth = 28.dp
    val BrowseIndexTapHeight = 20.dp
    val RailWidth = Dim.railWidth
    val CornerRadius = 11.dp // 对齐 style-guide「圆角 · 容器 11–12dp」
    val SheetRadius = 16.dp
    /** 全局描边统一为 1dp（style-guide §03），不再用 0.5dp。 */
    val Border = Dim.hairline
    /** 选中态描边比基线 hairline 更粗一档，仍配 clay 描边色一起用。 */
    val BorderSelected = 1.5.dp
    /** 手势判定阈值，非视觉 token，数值见 05-product-spec.md §1.4，与配色改版无关。 */
    val GestureThreshold = 48.dp
    val TwoFingerMinDistance = 60.dp
}

/** Sheet 与页面动效统一用这个 spring；参数来自 [Motion]，单一出处。 */
fun <T> motionSpec() = spring<T>(dampingRatio = Motion.SPRING_DAMPING, stiffness = Motion.SPRING_STIFFNESS)
