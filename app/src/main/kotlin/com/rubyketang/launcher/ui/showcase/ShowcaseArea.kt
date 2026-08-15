package com.rubyketang.launcher.ui.showcase

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.rubyketang.launcher.LauncherState
import com.rubyketang.launcher.data.ShowcaseMode
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.Palette
import com.rubyketang.launcher.ui.theme.Type

/**
 * 05-product-spec.md §2.5 展示区：海报单图 / 相册墙宫格，两种模式共用同一份图片列表。
 *
 * 首帧顺序遵守 §4.3："时间 + app 槽先上屏 → 展示区占位纯色块 → 图片异步填充"——每张图都用
 * `produceState` 起手渲染纯色占位 [Box]，解码完成前不阻塞任何东西；解码本身（含降采样、
 * 磁盘缓存）全在 [com.rubyketang.launcher.data.ShowcaseImageLoader] 里，这里只管排布。
 *
 * 点击进全屏查看器（Wave 5 下一步）；这一版先把点击回调留出接口，[onPhotoClick] 目前接的是空实现。
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ShowcaseArea(
    state: LauncherState,
    palette: Palette,
    modifier: Modifier = Modifier,
    onPhotoClick: (index: Int) -> Unit = {},
) {
    val mode by state.preferences.showcaseMode.collectAsState()
    val photos by state.showcasePhotos.collectAsState()
    val folderUri by state.preferences.showcaseFolderUri.collectAsState()

    // 授权（takePersistableUriPermission）是数据层的事，交给 LauncherState.setShowcaseFolder 做，
    // 这里只负责起 SAF 选择器、把结果原样转交——符合 Surface 不放业务逻辑的红线。
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) state.setShowcaseFolder(uri)
    }

    if (folderUri == null) {
        EmptyShowcaseHint(palette, modifier) { pickFolder.launch(null) }
        return
    }
    if (photos.isEmpty()) return // 文件夹选了但暂时没图/还没扫完，不占位打扰布局

    when (mode) {
        ShowcaseMode.POSTER -> PosterArea(state, palette, photos, modifier, onPhotoClick)
        ShowcaseMode.WALL -> WallArea(state, palette, photos, modifier, onPhotoClick)
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun EmptyShowcaseHint(palette: Palette, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(Dimens.Border, palette.line, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("选择图片文件夹", style = TextStyle(color = palette.fg2, fontSize = Type.Secondary))
    }
}

/** §2.5 模式一·海报：全宽单图，圆角 12dp，不做自动轮播动画——切换只发生在解锁那一刻。 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun PosterArea(
    state: LauncherState,
    palette: Palette,
    photos: List<Uri>,
    modifier: Modifier,
    onPhotoClick: (index: Int) -> Unit,
) {
    val index by state.showcaseIndex.collectAsState()
    val safeIndex = index.coerceIn(0, photos.lastIndex)
    val uri = photos[safeIndex]
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.line)
            .combinedClickable(onClick = { onPhotoClick(safeIndex) }),
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, widthPx, heightPx) {
            value = state.showcaseImages.thumbnail(uri, widthPx, heightPx)
        }
        bitmap?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/**
 * §2.5 模式二·相册墙：3 列宫格，2dp 描边模拟相框，间距 8dp；不翻页、不滚动，一次性呈现。
 * 最多显示 9 张（3×3）——"全部照片一次性呈现"是指墙本身不分页，不是不限张数；相册墙这个模式
 * 定位就是精选小文件夹，§4.3 也要求相册墙"驻留全部缩略图"，张数必须有界才守得住内存预算。
 */
@Composable
private fun WallArea(
    state: LauncherState,
    palette: Palette,
    photos: List<Uri>,
    modifier: Modifier,
    onPhotoClick: (index: Int) -> Unit,
) {
    val shown = photos.take(WALL_MAX_PHOTOS).withIndex().toList()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.chunked(WALL_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (index, uri) ->
                    WallTile(state, palette, uri, Modifier.weight(1f)) { onPhotoClick(index) }
                }
                // 最后一行不满 3 张时补空位，保持宫格对齐而不是最后一张被拉伸变形。
                repeat(WALL_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun WallTile(state: LauncherState, palette: Palette, uri: Uri, modifier: Modifier, onClick: () -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .border(2.dp, palette.line, RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick),
    ) {
        val sidePx = with(density) { maxWidth.roundToPx() }
        val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, sidePx) {
            value = state.showcaseImages.thumbnail(uri, sidePx, sidePx)
        }
        bitmap?.let {
            Image(it, null, Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        }
    }
}

private const val WALL_COLUMNS = 3
private const val WALL_MAX_PHOTOS = 9
