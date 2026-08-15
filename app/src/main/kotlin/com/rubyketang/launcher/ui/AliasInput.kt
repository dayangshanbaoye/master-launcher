package com.rubyketang.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.rubyketang.launcher.LauncherState
import com.rubyketang.launcher.model.Target
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.Palette
import com.rubyketang.launcher.ui.theme.Type

/**
 * P1-4 自定义别名：搜错过并最终点开的条目，长按"记住这个叫法"。
 * 别名写入索引（Target.aliases），永久生效。
 */
@Composable
fun AliasInput(
    state: LauncherState,
    target: Target,
    palette: Palette,
    onDismiss: () -> Unit,
) {
    var alias by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(Dimens.ScreenMargin)
                .fillMaxWidth()
                .border(Dimens.Border, palette.line, RoundedCornerShape(Dimens.SheetRadius))
                .background(palette.bg, RoundedCornerShape(Dimens.SheetRadius))
                .padding(16.dp)
        ) {
            BasicText(
                "给「${target.label}」记一个叫法",
                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )
            BasicTextField(
                value = alias,
                onValueChange = { alias = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .border(Dimens.Border, palette.fg2, RoundedCornerShape(Dimens.CornerRadius))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = TextStyle(color = palette.fg, fontSize = Type.Item),
                cursorBrush = SolidColor(palette.accent),
                singleLine = true,
            )
            Row(Modifier.padding(top = 16.dp)) {
                BasicText(
                    "记住",
                    Modifier
                        .clickable {
                            state.addAlias(target.id, alias)
                            onDismiss()
                        }
                        .padding(end = 20.dp),
                    style = TextStyle(color = palette.accent, fontSize = Type.Item),
                )
                BasicText(
                    "算了",
                    Modifier.clickable(onClick = onDismiss),
                    style = TextStyle(color = palette.fg2, fontSize = Type.Item),
                )
            }
        }
    }
}
