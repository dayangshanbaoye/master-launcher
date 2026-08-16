package com.rubyketang.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rubyketang.launcher.LauncherState
import com.rubyketang.launcher.model.Target
import com.rubyketang.launcher.ui.theme.Palette

/**
 * 长按条目的统一菜单：置顶 / 记住叫法 / 改分类。
 * 所有可调项就地调整，不做集中设置页。
 *
 * TODO(Wave 1)：按新手势模型加回"绑定手势"入口（05-product-spec.md §1.5 速查表就地改绑）。
 */
@Composable
fun TargetContextMenu(
    state: LauncherState,
    target: Target,
    palette: Palette,
    onDismiss: () -> Unit,
) {
    var aliasing by remember { mutableStateOf(false) }
    var evicting by remember { mutableStateOf(false) }
    var choosingCategory by remember { mutableStateOf(false) }
    // 菜单靠 keepOpen 留在原地支持"连续勾多个分类"，但置顶态和分类勾选态都不是从 State/Flow 读的
    // （isPinned 读的是普通 val 快照，tagsOf 读的是 tagResolver 内部可变 map），Compose 感知不到
    // 变化、不会自动重组。手动读一下这个计数器建立重组依赖，点击后自增，让菜单里的 ✓/· 立即刷新，
    // 不用关掉重开才能看到最新状态。
    var refreshTick by remember { mutableIntStateOf(0) }
    if (aliasing) {
        AliasInput(state, target, palette, onDismiss)
        return
    }
    if (evicting) {
        ContextMenu(
            title = "钉到首页 · 替换哪个？",
            palette = palette,
            onDismiss = onDismiss,
            items = state.fixedSlotTargets().mapIndexedNotNull { index, occupant ->
                occupant?.let { MenuItem(it.label) { state.setFixedSlot(index, target.id) } }
            },
        )
        return
    }
    if (choosingCategory) {
        // 11 个分类平铺在主菜单里会把长按弹层撑成接近 20 行、超屏没法滚动，收成二级列表。
        // §3.2.3 单条编辑：多选勾选，不用确认；keepOpen 让这一层也留在原地方便连续勾多个。
        // checked 和 toggleTag 的基准都读 state.tagsOf(target.id)（同步、实时），不用 target.tags
        // 这个长按那一刻的快照——否则连续勾两个会互相覆盖，见 LauncherState.toggleTag 的注释。
        ContextMenu(
            title = "改分类",
            palette = palette,
            onDismiss = onDismiss,
            items = run {
                refreshTick // 建立重组依赖，见上面声明处的说明
                state.allTagCategories().map { category ->
                    val checked = category in state.tagsOf(target.id)
                    MenuItem(category, keepOpen = true, selected = checked) {
                        state.toggleTag(target, category)
                        refreshTick++
                    }
                }
            },
        )
        return
    }

    ContextMenu(
        title = target.label,
        palette = palette,
        onDismiss = onDismiss,
        items = buildList {
            refreshTick // 建立重组依赖，见上面声明处的说明
            add(
                MenuItem(if (state.isPinned(target.id)) "取消置顶" else "置顶") {
                    state.togglePin(target.id)
                    refreshTick++
                }
            )
            add(MenuItem("记住叫法…", keepOpen = true) { aliasing = true })
            // §2.3：Search/Browse 长按也能直接钉到首页固定簇，不用非得先在 Canvas 里长按推荐槽。
            add(
                MenuItem("钉到首页", keepOpen = true) {
                    if (!state.pinRecommendedToFixed(target.id)) evicting = true else onDismiss()
                }
            )
            add(MenuItem("改分类…", keepOpen = true) { choosingCategory = true })
            state.dndActionsFor(target).forEach { action ->
                add(MenuItem(action.label) { state.toggleDnd(action.tag) })
            }
            if (!state.notificationAccessGranted()) {
                add(MenuItem("开启可操作通知") { state.requestNotificationAccess() })
            }
            add(MenuItem("同步到另一台设备") { state.syncToOtherDevice() })
        },
    )
}
