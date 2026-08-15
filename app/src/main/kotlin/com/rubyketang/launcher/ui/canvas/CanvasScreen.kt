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
import androidx.compose.ui.graphics.ColorFilter
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
    val top by produceState<List<ScoredTarget>>(emptyList(), version, dndVersion, notifications) {
        value = state.canvasEntries()
    }
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
                        .quickReferenceLongPress { quickReferenceVisible = it }
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
                    Hint("↑ 最近", palette)
                    state.gestureBindings().entries.take(2).forEach { (gestureId, targetId) ->
                        Hint("${cornerArrow(gestureId)} ${state.labelOf(targetId) ?: ""}", palette)
                    }
                }
            }
        }

        // P1-3 引擎提议：手势不是用户配的，是引擎长出来的
        val proposal by produceState<com.rubyketang.launcher.model.Target?>(null, version) {
            value = state.pendingProposal()
        }
        proposal?.let { target ->
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .fillMaxWidth()
                    .border(Dimens.Border, palette.accent, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                BasicText(
                    "${target.label} 这周每天都排前三",
                    style = TextStyle(color = palette.fg, fontSize = Type.Item),
                )
                BasicText(
                    "绑到角滑，以后一步就能开",
                    Modifier.padding(top = 8.dp),
                    style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
                )
                Row(Modifier.padding(top = 16.dp)) {
                    BasicText(
                        "绑定",
                        Modifier
                            .combinedClickable(onClick = { state.acceptProposal(target.id) })
                            .padding(end = 20.dp),
                        style = TextStyle(color = palette.accent, fontSize = Type.Secondary),
                    )
                    BasicText(
                        "不用",
                        Modifier.combinedClickable(onClick = { state.rejectProposal(target.id) }),
                        style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
                    )
                }
            }
        }

        if (quickReferenceVisible) {
            QuickReferenceOverlay(state.quickReferenceHints(), palette)
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

/** 长按空白处的瞬时说明层；绑定列表由 State 提供，Surface 只负责呈现。 */
@Composable
private fun QuickReferenceOverlay(hints: List<com.rubyketang.launcher.GestureHintEntry>, palette: Palette) {
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
            if (hints.isEmpty()) {
                BasicText(
                    "尚未绑定角滑",
                    Modifier.padding(top = 12.dp),
                    style = TextStyle(color = palette.fg, fontSize = Type.Item),
                )
            } else {
                hints.forEach { hint ->
                    BasicText(
                        "${cornerArrow(hint.gestureId)}  ${hint.label}",
                        Modifier.padding(top = 12.dp),
                        style = TextStyle(color = palette.fg, fontSize = Type.Item),
                    )
                }
            }
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
    Box(Modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.92f))) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .border(Dimens.Border, palette.line, RoundedCornerShape(Dimens.SheetRadius))
                .padding(20.dp),
        ) {
            BasicText("桌面控制", style = TextStyle(color = palette.fg, fontSize = Type.Item))
            BasicText(
                "下滑搜索 · 上滑最近任务 · 左滑返回 · 右滑切换界面",
                Modifier.padding(top = 8.dp),
                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )
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
            BasicText(
                "右上／右下等角滑：长按任意条目后绑定；点此清除全部绑定",
                Modifier
                    .combinedClickable(onClick = {
                        listOf("corner_tl", "corner_tr", "corner_bl", "corner_br").forEach(state::unbindGesture)
                    })
                    .padding(top = 18.dp),
                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )
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

private fun cornerArrow(gestureId: String): String = when (gestureId) {
    "corner_tl" -> "↘"
    "corner_tr" -> "↙"
    "corner_bl" -> "↗"
    "corner_br" -> "↖"
    else -> "·"
}
