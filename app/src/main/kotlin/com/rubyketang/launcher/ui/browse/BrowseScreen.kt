package com.rubyketang.launcher.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rubyketang.launcher.LauncherState
import com.rubyketang.launcher.LauncherSurface
import com.rubyketang.launcher.engine.tag.TagResolver
import com.rubyketang.launcher.engine.browse.CommonPrefix
import com.rubyketang.launcher.model.ScoredTarget
import com.rubyketang.launcher.model.Tag
import com.rubyketang.launcher.resolver.Query
import com.rubyketang.launcher.ui.TargetContextMenu
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.LocalUiScale
import com.rubyketang.launcher.ui.theme.Palette
import com.rubyketang.launcher.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * P0-7 Browse：master-detail，纯文字无图标。
 * 左栏分类（引擎生成），分类内按 frecency 排；"全部"挂拼音首字母索引条；
 * 分类切换用左右滑；共同前缀的差异部分渲染成次级色后缀。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(state: LauncherState, palette: Palette) {
    val uiScale = LocalUiScale.current
    val version by state.indexVersion.collectAsState()
    val dndVersion by state.dndVersion.collectAsState()
    val categories = remember(version, dndVersion) { state.categories() }
    var current by remember { mutableIntStateOf(0) }
    if (current >= categories.size) current = 0
    val category = categories.getOrElse(current) { TagResolver.ALL_APPS }

    val items = remember(category, version, dndVersion) {
        state.resolve(Query(tag = Tag(category), limit = Int.MAX_VALUE))
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menuTarget by remember { mutableStateOf<com.rubyketang.launcher.model.Target?>(null) }

    BackHandler { state.surface.value = LauncherSurface.CANVAS }

    Row(
        Modifier
            .fillMaxSize()
            .pointerInput(categories) {
                // 分类切换用左右滑，不用点
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        state.handleSurfaceGesture(if (acc < 0) "left" else "right")
                        acc = 0f
                    },
                    onHorizontalDrag = { change, drag ->
                        acc += drag
                        change.consume()
                    },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { state.goHome() }) }
    ) {
        // 左栏 76dp，放得下 3 个中文字；"全部"固定沉底，上方一条分隔线
        Column(
            Modifier
                .width(Dimens.RailWidth)
                .fillMaxHeight()
                .padding(vertical = 20.dp)
        ) {
            categories.forEachIndexed { i, name ->
                val selected = i == current
                if (name == TagResolver.ALL_APPS) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(Dimens.Border).background(palette.line))
                }
                BasicText(
                    name,
                    Modifier
                        .fillMaxWidth()
                        .clickable { current = i }
                        .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                    style = TextStyle(
                        color = if (selected) palette.accent else palette.fg2,
                        fontSize = Type.Secondary,
                    ),
                )
            }
        }
        Box(Modifier.width(Dimens.Border).fillMaxHeight().background(palette.line))

        // 右栏条目：文字加边框，无图标
        Box(Modifier.weight(1f).fillMaxHeight().padding(20.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                    ) {
                        items(items.size) { i ->
                            val scored = items[i]
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(Dimens.BrowseRowHeight)
                                    .padding(bottom = Dimens.BrowseRowSpacing)
                                    .border(Dimens.Border, palette.line, RoundedCornerShape(Dimens.CornerRadius))
                                    .combinedClickable(
                                        onClick = { state.launch(scored.target) },
                                        onLongClick = { menuTarget = scored.target },
                                    )
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = state.icons.icon(scored.target.iconUri)
                                    if (icon != null) {
                                        Image(
                                            bitmap = icon.bitmap,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimens.BrowseIconSize * uiScale),
                                            colorFilter = if (icon.isMonochrome) ColorFilter.tint(palette.fg2) else null,
                                        )
                                    } else {
                                        // 图标加载失败时也保留位置，避免列表文字左右跳动。
                                        Box(
                                            Modifier
                                                .size(Dimens.BrowseIconSize * uiScale)
                                                .border(Dimens.Border, palette.fg2, RoundedCornerShape(5.dp))
                                        )
                                    }
                                    Spacer(Modifier.width(Dimens.BrowseIconGap * uiScale))
                                    BasicText(
                                        withCommonPrefixSuffix(scored, items, i, palette),
                                        style = TextStyle(color = palette.fg, fontSize = Type.Item),
                                    )
                                }
                            }
                        }
                    }

                    if (category == TagResolver.ALL_APPS) {
                        Spacer(Modifier.width(Dimens.BrowseIndexGap))
                        val letters = remember(items) { items.map { state.initialOf(it.target.id) }.distinct() }
                        Column(
                            modifier = Modifier
                                .width(Dimens.BrowseIndexWidth)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            letters.forEach { letter ->
                                BasicText(
                                    letter,
                                    Modifier
                                        .fillMaxWidth()
                                        .height(Dimens.BrowseIndexTapHeight)
                                        .clickable {
                                            val index = items.indexOfFirst { state.initialOf(it.target.id) == letter }
                                            if (index >= 0) scope.launch { listState.scrollToItem(index) }
                                        },
                                    style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
                                )
                            }
                        }
                    }
                }
                BasicText(
                    if (category == TagResolver.ALL_APPS) "${items.size} 项 · 按拼音" else "${items.size} 项 · 按使用频率",
                    style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
                )
            }

        }
    }

    menuTarget?.let { target ->
        TargetContextMenu(state, target, palette) { menuTarget = null }
    }
}

/** 与相邻条目共享前缀（≥2 字）时，差异部分用次级文本色渲染成后缀。 */
private fun withCommonPrefixSuffix(
    scored: ScoredTarget,
    items: List<ScoredTarget>,
    index: Int,
    palette: Palette,
): AnnotatedString {
    val label = scored.target.label
    val siblings = listOfNotNull(items.getOrNull(index - 1), items.getOrNull(index + 1))
    val prefix = CommonPrefix.sharedPrefixLength(label, siblings.map { it.target.label })
    return buildAnnotatedString {
        if (prefix >= 2 && prefix < label.length) {
            append(label.substring(0, prefix))
            withStyle(SpanStyle(color = palette.fg2)) { append(label.substring(prefix)) }
        } else {
            append(label)
        }
    }
}
