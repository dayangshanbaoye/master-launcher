package com.rubyketang.launcher.ui.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rubyketang.launcher.LauncherState
import com.rubyketang.launcher.LauncherSurface
import com.rubyketang.launcher.model.ScoredTarget
import com.rubyketang.launcher.resolver.Query
import com.rubyketang.launcher.ui.TargetContextMenu
import com.rubyketang.launcher.ui.gesture.quickReferenceLongPress
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.LocalUiScale
import com.rubyketang.launcher.ui.theme.Palette
import com.rubyketang.launcher.ui.theme.Type
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P0-5 Canvas 主屏：时间 + 日期 + 引擎 top-4 + 底部手势提示条。
 * 不放图标网格；没有任何用户可拖拽、可摆放的元素。
 * 同时是手势的画布（P0-8）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CanvasScreen(state: LauncherState, palette: Palette, onEnableBluetooth: () -> Unit) {
    val uiScale = LocalUiScale.current
    val version by state.indexVersion.collectAsState()
    val dndVersion by state.dndVersion.collectAsState()
    val notifications by state.notificationEntries.collectAsState()
    val accessibilityGranted by state.accessibilityGranted.collectAsState()
    val top by produceState<List<ScoredTarget>>(emptyList(), version, dndVersion, notifications) {
        value = state.canvasEntries()
    }
    // §4.5：每次回到 Canvas 重新组合时刷新一次无障碍授权状态（国产 ROM 后台清理会静默关闭服务）。
    androidx.compose.runtime.LaunchedEffect(Unit) { state.refreshAccessibilityStatus() }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    var menuTarget by remember { mutableStateOf<com.rubyketang.launcher.model.Target?>(null) }
    var quickReferenceVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    // 长按菜单是"按住拖到目标行再松手"的经典交互（跟电源键菜单一样），不是分两次独立点击——
    // 浮层本身跟着长按手指的存活期出现/消失，所以命中判定得知道每一行画在哪、手指最终落在哪。
    var spacerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var quickReferenceTargets by remember { mutableStateOf(emptyMap<String, Rect>()) }

    Box(
        Modifier
            .fillMaxSize()
            .padding(Dimens.ScreenMargin)
    ) {
        Column(Modifier.fillMaxSize()) {
            BasicText(
                SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(now)),
                style = TextStyle(color = palette.fg, fontSize = Type.Clock, fontWeight = FontWeight.Light),
            )
            BasicText(
                SimpleDateFormat("E · M月d日", Locale.getDefault()).format(Date(now)),
                Modifier.padding(top = 6.dp),
                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )

            // 引擎排出的 top-4，用户不可拖拽
            Column(Modifier.weight(1f)) {
                Spacer(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { spacerCoordinates = it }
                        .quickReferenceLongPress(
                            onVisibilityChanged = { quickReferenceVisible = it },
                            onReleaseAt = { localPosition ->
                                val root = spacerCoordinates?.localToRoot(localPosition) ?: return@quickReferenceLongPress
                                when (quickReferenceTargets.entries.firstOrNull { (_, bounds) -> bounds.contains(root) }?.key) {
                                    "two_finger" -> state.preferences.cycleTwoFingerDownAction()
                                    "settings" -> settingsVisible = true
                                }
                            },
                        )
                )
                top.forEach { scored ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { state.launch(scored.target) },
                                onLongClick = { menuTarget = scored.target },
                            )
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = state.icons.icon(scored.target.iconUri)
                        if (icon != null) {
                            Image(
                                icon.bitmap, null,
                                Modifier.size(15.dp * uiScale),
                                colorFilter = if (icon.isMonochrome) ColorFilter.tint(palette.fg2) else null,
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(15.dp * uiScale)
                                    .border(Dimens.Border, palette.fg2, RoundedCornerShape(4.dp))
                            )
                        }
                        Spacer(Modifier.size(11.dp * uiScale))
                        BasicText(
                            scored.target.label,
                            style = TextStyle(color = palette.fg, fontSize = Type.Item),
                        )
                    }
                }
            }

            // 底部手势提示条（分隔靠一条 0.5dp 线）
            Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                Box(Modifier.fillMaxWidth().height(Dimens.Border).background(palette.line))
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Hint("↓ 搜索", palette)
                    Hint("→ 浏览", palette)
                    Hint("↑ 最近", palette)
                }
            }
        }

        if (quickReferenceVisible) {
            QuickReferenceOverlay(
                state = state,
                palette = palette,
                accessibilityGranted = accessibilityGranted,
                onMeasureTarget = { key, bounds ->
                    quickReferenceTargets = quickReferenceTargets + (key to bounds)
                },
            )
        }

        BasicText(
            "⚙",
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .combinedClickable(onClick = { settingsVisible = true })
                .padding(8.dp),
            style = TextStyle(color = palette.fg2, fontSize = Type.Item),
        )

        if (settingsVisible) {
            HomeSettingsSheet(
                state = state,
                palette = palette,
                onEnableBluetooth = onEnableBluetooth,
                onDismiss = { settingsVisible = false },
            )
        }

        menuTarget?.let { target ->
            TargetContextMenu(state, target, palette) { menuTarget = null }
        }
    }
}

/**
 * 长按空白处的手势速查表（05-product-spec.md §1.5）。按住不放显示，拖到某一行再松手即选中该项，
 * 松手位置不在任何可操作行上就只是单纯看一眼、什么都不触发——手指全程不离开屏幕，
 * 所以这里的行不能用普通 `combinedClickable`（那需要独立的按下-松开，长按浮层活不到那一下）。
 */
@Composable
private fun QuickReferenceOverlay(
    state: LauncherState,
    palette: Palette,
    accessibilityGranted: Boolean,
    onMeasureTarget: (key: String, bounds: Rect) -> Unit,
) {
    val twoFingerAction by state.preferences.twoFingerDownAction.collectAsState()
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(Dimens.ScreenMargin)
                .fillMaxWidth()
                .border(Dimens.Border, palette.line, RoundedCornerShape(Dimens.SheetRadius))
                .padding(20.dp),
        ) {
            BasicText("手势速查", style = TextStyle(color = palette.fg2, fontSize = Type.Secondary))
            BasicText(
                "拖到下面某一行再松手可操作",
                Modifier.padding(top = 2.dp),
                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )
            listOf(
                "↓ 下滑 · 搜索" to true,
                "→ 右滑 · 浏览" to true,
                "← 左滑 · 返回" to true,
                "双击空白 · 回首页" to true,
                "↑ 上滑 · 最近任务" to accessibilityGranted,
            ).forEach { (label, enabled) ->
                BasicText(
                    if (enabled) label else "$label（需授权）",
                    Modifier.padding(top = 12.dp),
                    style = TextStyle(color = if (enabled) palette.fg else palette.fg2, fontSize = Type.Item),
                )
            }
            val twoFingerLabel = when (twoFingerAction) {
                com.rubyketang.launcher.data.TwoFingerDownAction.NOTIFICATIONS -> "通知栏"
                com.rubyketang.launcher.data.TwoFingerDownAction.LOCK_SCREEN -> "锁屏"
                com.rubyketang.launcher.data.TwoFingerDownAction.NONE -> "留空"
            }
            val twoFingerNeedsAuth = twoFingerAction != com.rubyketang.launcher.data.TwoFingerDownAction.NONE && !accessibilityGranted
            val twoFingerSuffix = if (twoFingerNeedsAuth) "（需授权） · 拖到这行切换" else " · 拖到这行切换"
            BasicText(
                "双指下滑 · $twoFingerLabel$twoFingerSuffix",
                Modifier
                    .padding(top = 12.dp)
                    .onGloballyPositioned { onMeasureTarget("two_finger", it.boundsInRoot()) },
                style = TextStyle(color = if (twoFingerNeedsAuth) palette.fg2 else palette.fg, fontSize = Type.Item),
            )
            BasicText(
                "设置",
                Modifier
                    .padding(top = 20.dp)
                    .onGloballyPositioned { onMeasureTarget("settings", it.boundsInRoot()) },
                style = TextStyle(color = palette.accent, fontSize = Type.Item),
            )
        }
    }
}

/** 首页角落入口：仅放会改变系统桌面行为的少量控制，不把主界面变成传统设置页。 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun HomeSettingsSheet(
    state: LauncherState,
    palette: Palette,
    onEnableBluetooth: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scale by state.preferences.fontScale.collectAsState()
    val accessibilityGranted by state.accessibilityGranted.collectAsState()
    Box(Modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.92f))) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .border(Dimens.Border, palette.line, RoundedCornerShape(Dimens.SheetRadius))
                .padding(20.dp),
        ) {
            BasicText("桌面控制", style = TextStyle(color = palette.fg, fontSize = Type.Item))
            BasicText(
                "下滑搜索 · 右滑浏览 · 左滑返回 · 双击回首页 · 长按看手势速查表",
                Modifier.padding(top = 8.dp),
                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )
            if (!accessibilityGranted) {
                BasicText(
                    "开启无障碍才能用上滑最近任务 / 双指下滑通知栏",
                    Modifier
                        .combinedClickable(onClick = state::openAccessibilitySettings)
                        .padding(top = 12.dp),
                    style = TextStyle(color = palette.accent, fontSize = Type.Secondary),
                )
            }
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("字体与图标", style = TextStyle(color = palette.fg, fontSize = Type.Item))
                BasicText(
                    "−",
                    Modifier.combinedClickable(onClick = { state.preferences.setFontScale(scale - 0.05f) }).padding(start = 18.dp),
                    style = TextStyle(color = palette.accent, fontSize = Type.Item),
                )
                BasicText(
                    "${"%.0f".format(scale * 100)}%",
                    Modifier.padding(horizontal = 14.dp),
                    style = TextStyle(color = palette.fg, fontSize = Type.Item),
                )
                BasicText(
                    "+",
                    Modifier.combinedClickable(onClick = { state.preferences.setFontScale(scale + 0.05f) }),
                    style = TextStyle(color = palette.accent, fontSize = Type.Item),
                )
            }
            Row(Modifier.padding(top = 18.dp)) {
                BasicText(
                    "切换系统桌面",
                    Modifier.combinedClickable(onClick = state::openHomeSettings).padding(end = 22.dp),
                    style = TextStyle(color = palette.accent, fontSize = Type.Item),
                )
                BasicText(
                    "开启蓝牙",
                    Modifier.combinedClickable(onClick = onEnableBluetooth).padding(end = 22.dp),
                    style = TextStyle(color = palette.accent, fontSize = Type.Item),
                )
                BasicText(
                    "关闭",
                    Modifier.combinedClickable(onClick = onDismiss),
                    style = TextStyle(color = palette.fg2, fontSize = Type.Item),
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String, palette: Palette) {
    BasicText(text, style = TextStyle(color = palette.fg2, fontSize = Type.Secondary))
}
