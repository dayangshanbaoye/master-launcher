package com.rubyketang.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rubyketang.launcher.LauncherState
import com.rubyketang.launcher.engine.tag.TagResolver
import com.rubyketang.launcher.model.Target
import com.rubyketang.launcher.ui.theme.Palette

/**
 * 长按条目的统一菜单：置顶 / 记住叫法 / 改分类 / 绑到角滑。
 * 所有可调项就地调整，不做集中设置页。
 */
@Composable
fun TargetContextMenu(
    state: LauncherState,
    target: Target,
    palette: Palette,
    onDismiss: () -> Unit,
) {
    var aliasing by remember { mutableStateOf(false) }
    if (aliasing) {
        AliasInput(state, target, palette, onDismiss)
        return
    }

    val cornerNames = listOf(
        "corner_tl" to "↘ 左上起手",
        "corner_tr" to "↙ 右上起手",
        "corner_bl" to "↗ 左下起手",
        "corner_br" to "↖ 右下起手",
    )
    ContextMenu(
        title = target.label,
        palette = palette,
        onDismiss = onDismiss,
        items = buildList {
            add(
                MenuItem(if (state.isPinned(target.id)) "取消置顶" else "置顶") {
                    state.togglePin(target.id)
                }
            )
            add(MenuItem("记住叫法…", keepOpen = true) { aliasing = true })
            TagResolver.ALL.forEach { category ->
                add(MenuItem("改分类 → $category") { state.overrideTag(target.id, category) })
            }
            state.dndActionsFor(target).forEach { action ->
                add(MenuItem(action.label) { state.toggleDnd(action.tag) })
            }
            cornerNames.forEach { (gestureId, name) ->
                add(MenuItem("绑到角滑 $name") { state.bindGesture(gestureId, target.id) })
            }
            if (!state.notificationAccessGranted()) {
                add(MenuItem("开启可操作通知") { state.requestNotificationAccess() })
            }
            add(MenuItem("同步到另一台设备") { state.syncToOtherDevice() })
        },
    )
}
