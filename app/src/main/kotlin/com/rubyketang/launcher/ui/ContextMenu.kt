package com.rubyketang.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.Palette
import com.rubyketang.launcher.ui.theme.Type

data class MenuItem(val label: String, val keepOpen: Boolean = false, val onClick: () -> Unit)

/**
 * 条目长按的就地调整菜单（没有设置页，可调项都在这里）。
 * 无阴影无卡片填充，靠边框和留白分隔。
 */
@Composable
fun ContextMenu(
    title: String,
    items: List<MenuItem>,
    palette: Palette,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(Dimens.ScreenMargin)
                .fillMaxWidth()
                .border(Dimens.Border, palette.line, RoundedCornerShape(Dimens.SheetRadius))
                .background(palette.bg, RoundedCornerShape(Dimens.SheetRadius))
                .padding(vertical = 8.dp)
        ) {
            BasicText(
                title,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = androidx.compose.ui.text.TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )
            items.forEach { item ->
                BasicText(
                    item.label,
                    Modifier
                        .fillMaxWidth()
                        .clickable { item.onClick(); if (!item.keepOpen) onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    style = androidx.compose.ui.text.TextStyle(color = palette.fg, fontSize = Type.Item),
                )
            }
        }
    }
}
