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
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.Palette
import com.rubyketang.launcher.ui.theme.Type

/**
 * 05-product-spec.md §3.2.3 自定义分类：允许创建，上限 8 个；不能与固定表/已有自定义分类重名。
 * 校验（空名/重名/超上限）全在 TagResolver.addCustomCategory 里做（已有引擎测试覆盖），这里只
 * 负责收文本、把 [onCreate] 返回的错误信息（null = 成功）原样显示——不在 Surface 层重复校验规则。
 *
 * 创建成功后调用方负责把 [onCreate] 的成功分支导回"改分类"列表，不整个关掉长按菜单——
 * 跟这个文件同目录的其它二级输入（AliasInput）关的是整个菜单，这里不一样，是因为新建分类
 * 之后用户大概率想接着在列表里勾/继续操作，不是这一步就结束了。
 */
@Composable
fun CustomCategoryInput(
    palette: Palette,
    onCreate: (name: String) -> String?,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Box(
        Modifier.fillMaxSize().clickable(onClick = onCancel),
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
                "新建分类",
                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
            )
            BasicTextField(
                value = name,
                onValueChange = { name = it; error = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .border(Dimens.Border, palette.fg2, RoundedCornerShape(Dimens.CornerRadius))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = TextStyle(color = palette.fg, fontSize = Type.Item),
                cursorBrush = SolidColor(palette.accent),
                singleLine = true,
            )
            error?.let {
                BasicText(
                    it,
                    Modifier.padding(top = 8.dp),
                    style = TextStyle(color = palette.accent, fontSize = Type.Secondary),
                )
            }
            Row(Modifier.padding(top = 16.dp)) {
                BasicText(
                    "创建",
                    Modifier
                        .clickable { error = onCreate(name) }
                        .padding(end = 20.dp),
                    style = TextStyle(color = palette.accent, fontSize = Type.Item),
                )
                BasicText(
                    "算了",
                    Modifier.clickable(onClick = onCancel),
                    style = TextStyle(color = palette.fg2, fontSize = Type.Item),
                )
            }
        }
    }
}
