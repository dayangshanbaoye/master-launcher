package com.rubyketang.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.Dp
import com.rubyketang.launcher.data.IconCache
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.IconTreatment
import com.rubyketang.launcher.ui.theme.Shapes

/**
 * 三个 Surface 共用的图标渲染。style-guide.html §06：
 * 保留原色（不再区分"系统主题单色图标"，一律走这一套）、统一裁 [Shapes.icon] 圆角、
 * 降饱和 [IconTreatment.SATURATION]、亮度超 [IconTreatment.BRIGHTNESS_CEILING] 的
 * 图标叠一层中性灰防止在浅色主题里糊掉。
 *
 * icon 为 null 时渲染一个同尺寸的空心描边框占位，避免文字因为有没有图标而左右跳动
 * （之前 Search 没占位，Browse 占位圆角还对不上，这里统一成一份）。
 */
@Composable
fun AppIcon(
    icon: IconCache.IconAsset?,
    contentDescription: String?,
    size: Dp,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size)) {
        if (icon != null) {
            Image(
                bitmap = icon.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().clip(Shapes.icon),
                colorFilter = DesaturatedIconFilter,
            )
            if (icon.brightness > IconTreatment.BRIGHTNESS_CEILING) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(Shapes.icon)
                        .background(Color.Black.copy(alpha = IconTreatment.GREY_OVERLAY_ALPHA)),
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(Dimens.Border, borderColor, Shapes.icon),
            )
        }
    }
}

/** 算一次复用，不用每次重组都新建 ColorMatrix。 */
private val DesaturatedIconFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(IconTreatment.SATURATION) },
)
