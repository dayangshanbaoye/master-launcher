package com.rubyketang.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.Palette
import com.rubyketang.launcher.ui.theme.Type

data class MenuItem(
    val label: String,
    val keepOpen: Boolean = false,
    /** 当前生效项（比如"改分类"二级列表里的当前分类），渲染成强调色 + 打勾。 */
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

private val SheetTopCorners = RoundedCornerShape(
    topStart = Dimens.SheetRadius, topEnd = Dimens.SheetRadius,
    bottomEnd = 0.dp, bottomStart = 0.dp,
)

/**
 * 条目长按的就地调整菜单（没有设置页，可调项都在这里）。
 *
 * 底部 Sheet，不是居中悬浮卡：顶部固定一个抓手 + 标题 + 明确的"关闭"字样，正文超出
 * 屏幕高度时自己滚动。之前是居中卡片、没有滚动、也没有关闭按钮，长按菜单一多就整个
 * 摞在屏幕上，底下的项按不到、也不知道怎么退出去——这次改的就是这个。
 */
@Composable
fun ContextMenu(
    title: String,
    items: List<MenuItem>,
    palette: Palette,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.8f)
                .border(Dimens.Border, palette.line, SheetTopCorners)
                .background(palette.bg, SheetTopCorners)
        ) {
            // 抓手：纯装饰，标出这是一张独立的 sheet，不是跟背景糊在一起。
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 3.dp)
                    .background(palette.line, RoundedCornerShape(2.dp))
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    title,
                    Modifier.weight(1f),
                    style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
                )
                BasicText(
                    "关闭",
                    Modifier.clickable(onClick = onDismiss),
                    style = TextStyle(color = palette.accent, fontSize = Type.Secondary),
                )
            }
            Box(Modifier.fillMaxWidth().height(Dimens.Border).background(palette.line))
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp)
            ) {
                items.forEach { item ->
                    BasicText(
                        if (item.selected) "✓ ${item.label}" else item.label,
                        Modifier
                            .fillMaxWidth()
                            .clickable { item.onClick(); if (!item.keepOpen) onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        style = TextStyle(
                            color = if (item.selected) palette.accent else palette.fg,
                            fontSize = Type.Item,
                        ),
                    )
                }
            }
        }
    }
}
