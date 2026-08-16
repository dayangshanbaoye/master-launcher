package com.rubyketang.launcher.ui.canvas

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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rubyketang.launcher.CanvasSlots
import com.rubyketang.launcher.LauncherState
import com.rubyketang.launcher.LauncherSurface
import com.rubyketang.launcher.data.Handedness
import com.rubyketang.launcher.data.TwoFingerDownAction
import com.rubyketang.launcher.engine.calendar.LunarCalendar
import com.rubyketang.launcher.engine.canvas.ClockFormatter
import com.rubyketang.launcher.model.Target
import com.rubyketang.launcher.ui.AppIcon
import com.rubyketang.launcher.ui.ContextMenu
import com.rubyketang.launcher.ui.MenuItem
import com.rubyketang.launcher.ui.TargetContextMenu
import com.rubyketang.launcher.ui.gesture.quickReferenceLongPress
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.LocalUiScale
import com.rubyketang.launcher.ui.theme.Palette
import com.rubyketang.launcher.ui.theme.Type
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/**
 * P0-5 Canvas 主屏：时间 + 日期 + 固定簇/推荐簇 8 槽 + 底部手势提示条。
 * 应用槽不可拖拽摆放——固定簇由用户长按钉/换/清，推荐簇由引擎按迟滞规则选。
 * 同时是手势的画布（P0-8）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CanvasScreen(state: LauncherState, palette: Palette, onEnableBluetooth: () -> Unit, onSetDefaultLauncher: () -> Unit) {
    val uiScale = LocalUiScale.current
    val version by state.indexVersion.collectAsState()
    val dndVersion by state.dndVersion.collectAsState()
    val notifications by state.notificationEntries.collectAsState()
    val canvasSlotVersion by state.canvasSlotVersion.collectAsState()
    val accessibilityGranted by state.accessibilityGranted.collectAsState()
    val handedness by state.preferences.handedness.collectAsState()
    val onboardingActive by state.onboardingActive.collectAsState()
    val defaultLauncherChanged by state.defaultLauncherChanged.collectAsState()
    val slots by produceState(
        CanvasSlots(List(4) { null }, List(4) { null }, false),
        version, dndVersion, notifications, canvasSlotVersion,
    ) {
        value = state.canvasSlots()
    }
    // §4.5：每次回到 Canvas 重新组合时刷新一次无障碍授权状态（国产 ROM 后台清理会静默关闭服务）。
    androidx.compose.runtime.LaunchedEffect(Unit) { state.refreshAccessibilityStatus() }
    var menuTarget by remember { mutableStateOf<Target?>(null) }
    var slotAction by remember { mutableStateOf<SlotAction?>(null) }
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
            ClockArea(state, palette)

            if (defaultLauncherChanged) {
                DefaultLauncherBanner(state, palette, onSetDefaultLauncher, Modifier.padding(top = 14.dp))
            }

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

                // §2.3 固定簇（用户钉死）+ 推荐簇（引擎按迟滞规则选），推荐簇落在惯用手一侧。
                val fixedColumn = @Composable { modifier: Modifier ->
                    SlotColumn(
                        title = "固定", targets = slots.fixed, isFixed = true, learning = false,
                        state = state, palette = palette, uiScale = uiScale, modifier = modifier,
                        onEmptyFixedTap = { state.surface.value = LauncherSurface.SEARCH },
                        onLongPressFixed = { index, target -> slotAction = SlotAction.FixedMenu(index, target) },
                        onLongPressRecommended = { _, _ -> },
                    )
                }
                val recommendedColumn = @Composable { modifier: Modifier ->
                    SlotColumn(
                        title = "推荐", targets = slots.recommended, isFixed = false, learning = slots.learning,
                        state = state, palette = palette, uiScale = uiScale, modifier = modifier,
                        onEmptyFixedTap = {},
                        onLongPressFixed = { _, _ -> },
                        onLongPressRecommended = { index, target -> slotAction = SlotAction.RecommendedMenu(index, target) },
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    if (handedness == Handedness.LEFT) {
                        recommendedColumn(Modifier.weight(1f))
                        Spacer(Modifier.width(24.dp))
                        fixedColumn(Modifier.weight(1f))
                    } else {
                        fixedColumn(Modifier.weight(1f))
                        Spacer(Modifier.width(24.dp))
                        recommendedColumn(Modifier.weight(1f))
                    }
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
                onSetDefaultLauncher = onSetDefaultLauncher,
                onDismiss = { settingsVisible = false },
            )
        }

        menuTarget?.let { target ->
            TargetContextMenu(state, target, palette) { menuTarget = null }
        }

        when (val action = slotAction) {
            is SlotAction.FixedMenu -> ContextMenu(
                title = action.target.label,
                palette = palette,
                onDismiss = { slotAction = null },
                items = listOf(
                    MenuItem("更换") {
                        state.setFixedSlot(action.index, null)
                        state.surface.value = LauncherSurface.SEARCH
                    },
                    MenuItem("清空") { state.setFixedSlot(action.index, null) },
                ),
            )
            is SlotAction.RecommendedMenu -> ContextMenu(
                title = action.target.label,
                palette = palette,
                onDismiss = { slotAction = null },
                items = listOf(
                    MenuItem("钉住", keepOpen = true) {
                        slotAction = if (state.pinRecommendedToFixed(action.target.id)) {
                            null
                        } else {
                            SlotAction.Eviction(action.target.id, action.target.label)
                        }
                    },
                    MenuItem("排除（30 天不再推荐）") { state.excludeFromRecommendations(action.target.id) },
                ),
            )
            is SlotAction.Eviction -> ContextMenu(
                title = "钉住「${action.candidateLabel}」· 替换哪个？",
                palette = palette,
                onDismiss = { slotAction = null },
                items = state.fixedSlotTargets().mapIndexedNotNull { index, occupant ->
                    occupant?.let { MenuItem(it.label) { state.setFixedSlot(index, action.candidateId) } }
                },
            )
            null -> Unit
        }

        if (onboardingActive) {
            OnboardingOverlay(state, palette)
        }
    }
}

/** 长按固定/推荐槽弹出的操作菜单状态；钉住全满时转入替换选择。 */
private sealed interface SlotAction {
    data class FixedMenu(val index: Int, val target: Target) : SlotAction
    data class RecommendedMenu(val index: Int, val target: Target) : SlotAction
    data class Eviction(val candidateId: String, val candidateLabel: String) : SlotAction
}

/** 固定簇/推荐簇其中一列，4 槽竖排；推荐簇处于"正在学习"期时整列换成占位文案。 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SlotColumn(
    title: String,
    targets: List<Target?>,
    isFixed: Boolean,
    learning: Boolean,
    state: LauncherState,
    palette: Palette,
    uiScale: Float,
    modifier: Modifier,
    onEmptyFixedTap: () -> Unit,
    onLongPressFixed: (Int, Target) -> Unit,
    onLongPressRecommended: (Int, Target) -> Unit,
) {
    Column(modifier) {
        BasicText(title, style = TextStyle(color = palette.fg2, fontSize = Type.Secondary))
        Column(Modifier.padding(top = 8.dp)) {
            if (learning) {
                BasicText(
                    "正在学习你的习惯",
                    Modifier.padding(vertical = 7.dp),
                    style = TextStyle(color = palette.fg2, fontSize = Type.Item),
                )
            } else {
                targets.forEachIndexed { index, target ->
                    SlotRow(
                        target = target,
                        isFixed = isFixed,
                        state = state,
                        palette = palette,
                        uiScale = uiScale,
                        onClick = {
                            if (target != null) state.launch(target) else if (isFixed) onEmptyFixedTap()
                        },
                        onLongClick = {
                            if (target == null) return@SlotRow
                            if (isFixed) onLongPressFixed(index, target) else onLongPressRecommended(index, target)
                        },
                    )
                }
            }
        }
    }
}

/** 单个应用槽：有内容渲染图标+名称；固定簇的空槽渲染虚线占位框（§2.3：空槽不自动填充）。 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SlotRow(
    target: Target?,
    isFixed: Boolean,
    state: LauncherState,
    palette: Palette,
    uiScale: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (target != null) {
            val icon = state.icons.icon(target.iconUri)
            AppIcon(
                icon = icon,
                // §4.10 TalkBack：app 槽必须有 contentDescription，手势对无障碍用户不可用时这是唯一入口。
                contentDescription = target.label,
                size = 15.dp * uiScale,
                borderColor = palette.fg2,
            )
            Spacer(Modifier.size(11.dp * uiScale))
            BasicText(target.label, style = TextStyle(color = palette.fg, fontSize = Type.Item))
        } else {
            Box(Modifier.size(15.dp * uiScale).dashedBorder(palette.line))
            if (isFixed) {
                Spacer(Modifier.size(11.dp * uiScale))
                BasicText("空", style = TextStyle(color = palette.fg2, fontSize = Type.Item))
            }
        }
    }
}

private fun Modifier.dashedBorder(color: Color, cornerRadius: androidx.compose.ui.unit.Dp = 4.dp) = this.drawBehind {
    val stroke = Stroke(
        width = Dimens.Border.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f),
    )
    drawRoundRect(color = color, style = stroke, cornerRadius = CornerRadius(cornerRadius.toPx()))
}

/** §4.6：确认过是默认桌面之后又被换掉，首页浮一条低干扰、可划掉的提示。 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun DefaultLauncherBanner(
    state: LauncherState,
    palette: Palette,
    onSetDefaultLauncher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .border(Dimens.Border, palette.line, RoundedCornerShape(Dimens.SheetRadius))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("默认桌面已被更改", style = TextStyle(color = palette.fg2, fontSize = Type.Secondary))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "设为默认",
                Modifier.combinedClickable(onClick = onSetDefaultLauncher).padding(end = 16.dp),
                style = TextStyle(color = palette.accent, fontSize = Type.Secondary),
            )
            BasicText(
                "×",
                Modifier.combinedClickable(onClick = { state.dismissDefaultLauncherBanner() }),
                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )
        }
    }
}

/** §4.2 冷启动引导：无使用数据、未配置固定槽的新用户，选最多 4 个钉到首页，可跳过。 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun OnboardingOverlay(state: LauncherState, palette: Palette) {
    val candidates = remember { state.onboardingCandidates() }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    Box(
        Modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(Dimens.ScreenMargin)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .border(Dimens.Border, palette.line, RoundedCornerShape(Dimens.SheetRadius))
                .padding(20.dp),
        ) {
            BasicText("欢迎", style = TextStyle(color = palette.fg2, fontSize = Type.Secondary))
            BasicText(
                "选最多 4 个最常用的钉到首页固定簇，可跳过",
                Modifier.padding(top = 2.dp),
                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )
            Column(
                Modifier
                    .padding(top = 14.dp)
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                candidates.forEach { target ->
                    val checked = target.id in selected
                    BasicText(
                        "${if (checked) "✓" else "·"} ${target.label}",
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {
                                selected = when {
                                    checked -> selected - target.id
                                    selected.size < 4 -> selected + target.id
                                    else -> selected
                                }
                            })
                            .padding(vertical = 8.dp),
                        style = TextStyle(color = if (checked) palette.fg else palette.fg2, fontSize = Type.Item),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                SettingsAction("跳过", palette, { state.skipOnboarding() }, accented = false)
                SettingsAction("完成", palette, { state.completeOnboarding(selected.toList()) })
            }
        }
    }
}

/**
 * 05-product-spec.md §2.2 时钟区：四元素（时间恒显示，星期/日期/农历可勾选），
 * 副元素数量决定排布（0 个→48sp 无副行；1-3 个→32sp + 1-2 行副行）。
 *
 * 分钟跳变只重绘时间文本，不重组整个时钟区——`now` 只有时间那一行的 BasicText 直接读，
 * 副行内容通过 `remember(dayKey, ...)` 按天粒度缓存，同一天内的分钟 tick 不会让副行重新排版。
 */
@Composable
private fun ClockArea(state: LauncherState, palette: Palette) {
    val showWeekday by state.preferences.showWeekday.collectAsState()
    val showDate by state.preferences.showDate.collectAsState()
    val showLunar by state.preferences.showLunar.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }

    val dayKey = now / 86_400_000L
    val lines = remember(dayKey, showWeekday, showDate, showLunar) {
        val date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val lunar = if (showLunar) LunarCalendar.of(date) else null
        ClockFormatter.format(date, showWeekday, showDate, showLunar, lunar)
    }
    val hour = remember(now / 60_000L) {
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime()
    }

    BasicText(
        "%d:%02d".format(hour.hour, hour.minute),
        style = TextStyle(
            color = palette.fg,
            fontSize = if (lines.sublines.isEmpty()) Type.ClockLarge else Type.ClockWithSub,
            fontWeight = FontWeight.Light,
            fontFeatureSettings = "tnum", // 数字等宽，分钟跳变时整行宽度不抖动
        ),
    )
    lines.sublines.forEachIndexed { index, line ->
        BasicText(
            line,
            Modifier.padding(top = if (index == 0) 6.dp else 2.dp),
            style = TextStyle(
                color = palette.fg2,
                fontSize = if (lines.sublines.size == 2) Type.Secondary else Type.ClockSubline,
            ),
        )
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
                TwoFingerDownAction.NOTIFICATIONS -> "通知栏"
                TwoFingerDownAction.LOCK_SCREEN -> "锁屏"
                TwoFingerDownAction.NONE -> "留空"
            }
            val twoFingerNeedsAuth = twoFingerAction != TwoFingerDownAction.NONE && !accessibilityGranted
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

/**
 * 05-product-spec.md §2.4 设置入口：分组固定为 桌面/手势/时钟/相册/数据/关于。
 * 纪律条款（CLAUDE.md 红线 #5）：只放系统级开关，排序权重/分类规则/迟滞天数这类引擎参数
 * 一律不进这里。相册（Wave 5）、数据（Wave 6）还没做，先占位，免得这张 Sheet 被推倒重做。
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun HomeSettingsSheet(
    state: LauncherState,
    palette: Palette,
    onEnableBluetooth: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scale by state.preferences.fontScale.collectAsState()
    val accessibilityGranted by state.accessibilityGranted.collectAsState()
    val handedness by state.preferences.handedness.collectAsState()
    val showWeekday by state.preferences.showWeekday.collectAsState()
    val showDate by state.preferences.showDate.collectAsState()
    val showLunar by state.preferences.showLunar.collectAsState()

    // fillMaxSize + clickable(onDismiss) 吃掉浮层范围内所有触摸——不然内容行之间的空隙会让点击
    // 穿透到背后的 Canvas，误触发 app 图标（真机测试踩到过，见 ContextMenu.kt 已有的同款写法）。
    Box(Modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.92f)).combinedClickable(onClick = onDismiss)) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .border(Dimens.Border, palette.line, RoundedCornerShape(Dimens.SheetRadius))
                .padding(20.dp),
        ) {
            SettingsGroup("桌面", palette, isFirst = true) {
                SettingsAction("选择默认桌面", palette, state::openHomeSettings)
                SettingsAction("设为默认桌面", palette, onSetDefaultLauncher)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
            }
            SettingsGroup("手势", palette) {
                BasicText(
                    "下滑搜索 · 右滑浏览 · 左滑返回 · 双击回首页 · 长按看手势速查表",
                    style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
                )
                Row(Modifier.padding(top = 10.dp)) {
                    HandednessOption("惯用右手", Handedness.RIGHT, handedness, palette, state.preferences::setHandedness)
                    HandednessOption("惯用左手", Handedness.LEFT, handedness, palette, state.preferences::setHandedness)
                }
                if (accessibilityGranted) {
                    BasicText(
                        "无障碍已开启",
                        Modifier.padding(top = 10.dp),
                        style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
                    )
                } else {
                    SettingsAction(
                        "开启无障碍才能用上滑最近任务 / 双指下滑通知栏",
                        palette,
                        state::openAccessibilitySettings,
                        topPadding = 10.dp,
                    )
                }
                SettingsAction("开启蓝牙", palette, onEnableBluetooth, topPadding = 10.dp)
            }
            SettingsGroup("时钟", palette) {
                ToggleRow("星期", showWeekday, palette, state.preferences::setShowWeekday)
                ToggleRow("日期", showDate, palette, state.preferences::setShowDate)
                ToggleRow("农历", showLunar, palette, state.preferences::setShowLunar)
            }
            SettingsGroup("相册", palette) {
                BasicText("即将支持", style = TextStyle(color = palette.fg2, fontSize = Type.Secondary))
            }
            SettingsGroup("数据", palette) {
                BasicText("即将支持", style = TextStyle(color = palette.fg2, fontSize = Type.Secondary))
            }
            SettingsGroup("关于", palette) {
                BasicText("Master Launcher 0.1", style = TextStyle(color = palette.fg2, fontSize = Type.Secondary))
            }
            SettingsAction("关闭", palette, onDismiss, topPadding = 20.dp, accented = false)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, palette: Palette, isFirst: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = if (isFirst) 0.dp else 18.dp)) {
        BasicText(title, style = TextStyle(color = palette.fg2, fontSize = Type.Secondary))
        Column(Modifier.padding(top = 8.dp)) { content() }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SettingsAction(
    label: String,
    palette: Palette,
    onClick: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    accented: Boolean = true,
) {
    BasicText(
        label,
        Modifier.combinedClickable(onClick = onClick).padding(top = topPadding),
        style = TextStyle(color = if (accented) palette.accent else palette.fg2, fontSize = Type.Item),
    )
}

/** 没有勾选框控件，用颜色 + 前缀符号表达开关状态，跟这套无装饰视觉一致。 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun ToggleRow(label: String, checked: Boolean, palette: Palette, onToggle: (Boolean) -> Unit) {
    BasicText(
        "${if (checked) "✓" else "·"} $label",
        Modifier
            .combinedClickable(onClick = { onToggle(!checked) })
            .padding(top = 8.dp),
        style = TextStyle(color = if (checked) palette.fg else palette.fg2, fontSize = Type.Item),
    )
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun HandednessOption(
    label: String,
    value: Handedness,
    current: Handedness,
    palette: Palette,
    onSelect: (Handedness) -> Unit,
) {
    val selected = value == current
    BasicText(
        "${if (selected) "✓" else "·"} $label",
        Modifier
            .combinedClickable(onClick = { onSelect(value) })
            .padding(end = 22.dp),
        style = TextStyle(color = if (selected) palette.accent else palette.fg2, fontSize = Type.Item),
    )
}

