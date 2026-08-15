package com.rubyketang.launcher.ui.recent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.rubyketang.launcher.LauncherState
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.Palette
import com.rubyketang.launcher.ui.theme.Type

/** 覆盖在首页上的任务概览模拟层；Android 未向第三方 Launcher 开放系统 Overview 的调用。 */
@Composable
fun TaskOverviewOverlay(state: LauncherState, palette: Palette, onDismiss: () -> Unit) {
    val targets = state.recentTargets()
    Box(Modifier.fillMaxSize().background(palette.bg.copy(alpha = 0.94f)).padding(Dimens.ScreenMargin)) {
        Column(Modifier.fillMaxSize()) {
            BasicText("任务概览", style = TextStyle(color = palette.fg, fontSize = Type.Clock))
            BasicText(
                "最近使用的应用",
                Modifier.padding(top = 6.dp),
                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )
            Spacer(Modifier.height(24.dp))
            if (!state.usageAccessGranted()) {
                BasicText(
                    "授权“使用情况访问”后显示最近打开的应用。Android 不向第三方桌面提供真实任务缩略图。",
                    style = TextStyle(color = palette.fg2, fontSize = Type.Item),
                )
                BasicText(
                    "去授权",
                    Modifier.clickable { state.requestUsageAccess() }.padding(top = 16.dp),
                    style = TextStyle(color = palette.accent, fontSize = Type.Item),
                )
            } else if (targets.isEmpty()) {
                BasicText("暂无最近使用的应用", style = TextStyle(color = palette.fg2, fontSize = Type.Item))
            } else {
                targets.forEach { target ->
                    val icon = state.icons.icon(target.iconUri)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(bottom = 8.dp)
                            .border(Dimens.Border, palette.line, RoundedCornerShape(Dimens.CornerRadius))
                            .clickable { state.launch(target) }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            icon?.let {
                                Image(
                                    it.bitmap,
                                    null,
                                    Modifier.size(24.dp),
                                    colorFilter = if (it.isMonochrome) ColorFilter.tint(palette.fg2) else null,
                                )
                                Spacer(Modifier.size(12.dp))
                            }
                            BasicText(target.label, style = TextStyle(color = palette.fg, fontSize = Type.Item))
                        }
                    }
                }
            }
            BasicText(
                "关闭",
                Modifier.clickable { onDismiss() }.padding(top = 16.dp),
                style = TextStyle(color = palette.fg2, fontSize = Type.Item),
            )
        }
    }
}
