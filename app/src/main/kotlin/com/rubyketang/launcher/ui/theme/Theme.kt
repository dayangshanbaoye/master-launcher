package com.rubyketang.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Master Launcher · Clay Orange
 * 与 design/style-guide.html 一一对应。改色值请同时改那份文件。
 *
 * 纪律：Clay 只出现在三处 —— 当前选中项、引擎推荐或提议、排行榜序号。
 *      其余一律灰阶。
 */

// ── 色板 ────────────────────────────────────────────

object ClayLight {
    val clay        = Color(0xFFD97757)
    val clayDeep    = Color(0xFFBE5F42)
    val clayWash    = Color(0xFFF6E7E0)
    val bg          = Color(0xFFF5F3EE)
    val raised      = Color(0xFFFFFDFA)
    val sunk        = Color(0xFFEDEAE3)
    val text        = Color(0xFF2C2926)
    val text2       = Color(0xFF7A736B)
    val text3       = Color(0xFFA29A90)
    val line        = Color(0xFFE2DDD4)
    val lineStrong  = Color(0xFFCFC8BC)
}

object ClayDark {
    val clay        = Color(0xFFE08A6B)
    val clayDeep    = Color(0xFFC97050)
    val clayWash    = Color(0xFF3A2620)
    val bg          = Color(0xFF1A1817)
    val raised      = Color(0xFF24211F)
    val sunk        = Color(0xFF141211)
    val text        = Color(0xFFEDE9E3)
    val text2       = Color(0xFF968E85)
    val text3       = Color(0xFF6C655D)
    val line        = Color(0xFF332F2B)
    val lineStrong  = Color(0xFF494340)
}

/** Material 没有 text3 / line / wash 这些角色，用自定义 CompositionLocal 带出去。 */
data class LauncherColors(
    val clay: Color, val clayDeep: Color, val clayWash: Color,
    val bg: Color, val raised: Color, val sunk: Color,
    val text: Color, val text2: Color, val text3: Color,
    val line: Color, val lineStrong: Color,
    val isDark: Boolean,
)

val LocalColors = staticCompositionLocalOf {
    LauncherColors(
        ClayLight.clay, ClayLight.clayDeep, ClayLight.clayWash,
        ClayLight.bg, ClayLight.raised, ClayLight.sunk,
        ClayLight.text, ClayLight.text2, ClayLight.text3,
        ClayLight.line, ClayLight.lineStrong, isDark = false,
    )
}

// ── 字体 ────────────────────────────────────────────
// 单一无衬线字族。数字一律 tabular，否则时钟每分钟跳变会整行抖动。
// 字重只用 300 / 400 / 500，不用 600 以上。

private val Sans = FontFamily.SansSerif

object LauncherType {
    /** 时钟 · 0 个副元素 */
    val clockXl = TextStyle(
        fontFamily = Sans, fontSize = 44.sp, fontWeight = FontWeight.Light,
        letterSpacing = (-1.5).sp, textAlign = TextAlign.Start,
    )
    /** 时钟 · 有副元素 */
    val clock = TextStyle(
        fontFamily = Sans, fontSize = 32.sp, fontWeight = FontWeight.Light,
        letterSpacing = (-0.96).sp,
    )
    val title = TextStyle(fontFamily = Sans, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val row   = TextStyle(fontFamily = Sans, fontSize = 13.5.sp, fontWeight = FontWeight.Normal)
    val field = TextStyle(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val meta  = TextStyle(fontFamily = Sans, fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
    val eyebrow = TextStyle(
        fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.9.sp,
    )
}

// ── 度量 ────────────────────────────────────────────

object Dim {
    val screenPad     = 20.dp   // 屏幕左右留白
    val listPad       = 14.dp   // Browse 右栏
    val railWidth     = 82.dp   // Browse 左栏
    val browseRow     = 40.dp   // Browse 行高
    val iconSize      = 22.dp   // 应用图标
    val iconGap       = 11.dp   // 图文间距
    val resultGap     = 16.dp   // Search 结果间距
    val touchMin      = 44.dp   // 最小热区，视觉可更小
    val hairline      = 1.dp    // 全局统一，不用 0.5dp
}

object Shapes {
    val icon      = RoundedCornerShape(6.dp)
    val container = RoundedCornerShape(11.dp)
    val stage     = RoundedCornerShape(12.dp)
    val frameOuter = RoundedCornerShape(4.dp)   // 相册墙相框
    val frameInner = RoundedCornerShape(2.dp)
    val pill      = RoundedCornerShape(999.dp)
}

// ── 动效 ────────────────────────────────────────────
// 四条，没有第五条。系统开启"减少动画"时全部降级为直接切换。

object Motion {
    const val SPRING_DAMPING = 0.85f
    const val SPRING_STIFFNESS = 400f
    const val SHARED_ELEMENT_MS = 280   // 相框 → 全屏
    const val SELECT_MS = 120           // 选中态变色，无缩放无涟漪
    const val PROPOSAL_MS = 200         // 提议卡淡入，不弹跳
}

// ── 应用图标处理 ─────────────────────────────────────
// Browse 用真实应用图标。不去色（去色即不可辨识），但要压到同一视觉层级。

object IconTreatment {
    /** ColorMatrix 饱和度。够压住艳色，又不影响识别。 */
    const val SATURATION = 0.85f
    /** 整体亮度超过此值的图标叠一层中性灰，防止白底图标在浅色主题里糊掉。 */
    const val BRIGHTNESS_CEILING = 0.82f
    const val GREY_OVERLAY_ALPHA = 0.06f
    // 不给图标加背景块或描边 —— 加了之后一列全是方框，比图标本身更吵。
    // 选中态只改文字色和行底色，不给图标加任何高亮。
}

// ── Theme ───────────────────────────────────────────

@Composable
fun MasterLauncherTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (dark) {
        LauncherColors(
            ClayDark.clay, ClayDark.clayDeep, ClayDark.clayWash,
            ClayDark.bg, ClayDark.raised, ClayDark.sunk,
            ClayDark.text, ClayDark.text2, ClayDark.text3,
            ClayDark.line, ClayDark.lineStrong, isDark = true,
        )
    } else {
        LauncherColors(
            ClayLight.clay, ClayLight.clayDeep, ClayLight.clayWash,
            ClayLight.bg, ClayLight.raised, ClayLight.sunk,
            ClayLight.text, ClayLight.text2, ClayLight.text3,
            ClayLight.line, ClayLight.lineStrong, isDark = false,
        )
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = c.clay, onPrimary = Color.White,
            primaryContainer = c.clayWash, onPrimaryContainer = c.clay,
            background = c.bg, onBackground = c.text,
            surface = c.raised, onSurface = c.text,
            surfaceVariant = c.sunk, onSurfaceVariant = c.text2,
            outline = c.lineStrong, outlineVariant = c.line,
        )
    } else {
        lightColorScheme(
            primary = c.clay, onPrimary = Color.White,
            primaryContainer = c.clayWash, onPrimaryContainer = c.clay,
            background = c.bg, onBackground = c.text,
            surface = c.raised, onSurface = c.text,
            surfaceVariant = c.sunk, onSurfaceVariant = c.text2,
            outline = c.lineStrong, outlineVariant = c.line,
        )
    }

    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                headlineLarge = LauncherType.clockXl,
                headlineMedium = LauncherType.clock,
                titleMedium = LauncherType.title,
                bodyLarge = LauncherType.field,
                bodyMedium = LauncherType.row,
                bodySmall = LauncherType.meta,
                labelSmall = LauncherType.eyebrow,
            ),
            content = content,
        )
    }
}
