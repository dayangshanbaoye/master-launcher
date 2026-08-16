package com.rubyketang.launcher.ui.showcase

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import com.rubyketang.launcher.data.ShowcaseImageLoader
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.motionSpec
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.launch

/** 打开全屏查看器的一次请求：整份照片列表 + 从哪张开始 + 被点那张缩略图当时在屏幕上的位置。 */
data class ShowcaseViewerRequest(
    val photos: List<Uri>,
    val startIndex: Int,
    val sourceBoundsInRoot: Rect,
)

/**
 * 05-product-spec.md §2.5 全屏查看器，海报/相册墙两种模式共用：
 * - 共享元素过渡：从 [ShowcaseViewerRequest.sourceBoundsInRoot] 放大到全屏，关闭时缩回原位
 * - 左右滑翻页，到头不循环（[HorizontalPager] 自带，不再重复实现）
 * - 双击 1x/2x、双指自由缩放、缩放时单指平移
 * - 下滑 / 系统返回键 / 点击图外空白 = 关闭
 * - §4.10：系统"减少动画"打开时跳过共享元素过渡，直接切换
 *
 * 独占触摸：挂载这个 Composable 期间，调用方（MainActivity.LauncherRoot）要把全局手势层
 * 和双击回首页那层一并摘掉，不能只指望这里的手势判定"抢赢"全局层。
 */
@Composable
fun ShowcaseViewerOverlay(
    request: ShowcaseViewerRequest,
    imageLoader: ShowcaseImageLoader,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reducedMotion = remember { isReducedMotionEnabled(context) }
    val slopPx = with(density) { Dimens.GestureThreshold.toPx() }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val pagerState = rememberPagerState(initialPage = request.startIndex) { request.photos.size }

    // 开场共享元素过渡：0 = 缩在原缩略图位置，1 = 铺满全屏。减少动画时直接定格在 1，不跑过渡。
    // 这个用 Animatable：只在 LaunchedEffect/协程里驱动，不需要在受限的手势协程里同步赋值。
    val transition = remember { Animatable(if (reducedMotion) 1f else 0f) }
    // 下滑跟手 + 捏合缩放/平移：手势判定跑在 AwaitPointerEventScope（@RestrictsSuspension）里，
    // 只能同步赋值，不能调用 Animatable.snapTo 这类挂起函数，所以这四个用普通 MutableState；
    // 需要缓动的地方（弹回/丢出去/双击缩放）临时开一个 Animatable 写回同一个 State，见下面几个函数。
    val dragOffsetY = remember { mutableStateOf(0f) }
    val scale = remember { mutableStateOf(1f) }
    val offsetX = remember { mutableStateOf(0f) }
    val offsetY = remember { mutableStateOf(0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!reducedMotion) transition.animateTo(1f, motionSpec())
    }
    // 翻页后重置缩放/平移——不能让上一张的放大状态带到下一张。
    LaunchedEffect(pagerState.settledPage) {
        scale.value = 1f
        offsetX.value = 0f
        offsetY.value = 0f
    }

    fun closeWithSharedElement() {
        if (closing) return
        closing = true
        scope.launch {
            if (!reducedMotion) transition.animateTo(0f, motionSpec())
            onDismiss()
        }
    }

    fun closeByDroppingAway() {
        if (closing) return
        closing = true
        scope.launch {
            val anim = Animatable(dragOffsetY.value)
            anim.animateTo(containerSize.height.toFloat().coerceAtLeast(600f), motionSpec()) { dragOffsetY.value = value }
            onDismiss()
        }
    }

    fun springDragBack() {
        scope.launch {
            val anim = Animatable(dragOffsetY.value)
            anim.animateTo(0f, motionSpec()) { dragOffsetY.value = value }
        }
    }

    BackHandler { closeWithSharedElement() }

    val containerW = containerSize.width.toFloat().coerceAtLeast(1f)
    val containerH = containerSize.height.toFloat().coerceAtLeast(1f)
    val sourceRect = request.sourceBoundsInRoot
    val dragFraction = (dragOffsetY.value / containerH).coerceIn(0f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (0.92f * transition.value) * (1f - dragFraction))),
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 共享元素：从缩略图矩形放大到全屏，用 transition 驱动；下滑跟手叠加在这份变换上面。
                    scaleX = lerp(sourceRect.width / containerW, 1f, transition.value)
                    scaleY = lerp(sourceRect.height / containerH, 1f, transition.value)
                    translationX = lerp(sourceRect.left, 0f, transition.value)
                    translationY = lerp(sourceRect.top, 0f, transition.value) + dragOffsetY.value
                    alpha = 1f - dragFraction * 0.5f
                }
                .pointerInput(pagerState.currentPage) {
                    detectViewerDragAndZoom(
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        dragOffsetY = dragOffsetY,
                        slopPx = slopPx,
                        containerSizePx = containerSize,
                        onDismissByDrag = ::closeByDroppingAway,
                        onSpringBack = ::springDragBack,
                    )
                }
                .pointerInput(pagerState.currentPage) {
                    detectTapGestures(
                        onDoubleTap = {
                            scope.launch {
                                val target = ViewerZoom.toggleForDoubleTap(scale.value)
                                val anim = Animatable(scale.value)
                                anim.animateTo(target, motionSpec()) { scale.value = value }
                                if (target <= 1f) {
                                    offsetX.value = 0f
                                    offsetY.value = 0f
                                }
                            }
                        },
                        onTap = { if (scale.value <= 1.01f) closeWithSharedElement() },
                    )
                },
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = scale.value <= 1.01f,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ShowcaseViewerPage(
                    uri = request.photos[page],
                    imageLoader = imageLoader,
                    maxDimensionPx = maxOf(containerSize.width, containerSize.height),
                    scaleX = if (page == pagerState.currentPage) scale.value else 1f,
                    offsetXPx = if (page == pagerState.currentPage) offsetX.value else 0f,
                    offsetYPx = if (page == pagerState.currentPage) offsetY.value else 0f,
                )
            }
        }
    }
}

@Composable
private fun ShowcaseViewerPage(
    uri: Uri,
    imageLoader: ShowcaseImageLoader,
    maxDimensionPx: Int,
    scaleX: Float,
    offsetXPx: Float,
    offsetYPx: Float,
) {
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, maxDimensionPx) {
        if (maxDimensionPx > 0) bitmap = imageLoader.fullRes(uri, maxDimensionPx)
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleX
                this.translationX = offsetXPx
                this.translationY = offsetYPx
            },
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

/**
 * 独占的手势判定：双指按下即进入捏合缩放（并在只剩一指时继续按平移处理，覆盖"捏合后接着单指拖"）；
 * 已经放大过（>1x）时单指直接平移；1x 状态下单指移动先攒着，一旦超过 [slopPx] 才判定主轴方向——
 * 竖直向下占优才算下滑关闭手势并开始 consume，水平或向上的移动完全不 consume，交还给
 * [HorizontalPager] 自己的横向翻页检测（跟 GestureLayer 里 detectTwoFingerDownSwipe 同一个纪律：
 * 判定前不吃事件）。
 */
private suspend fun PointerInputScope.detectViewerDragAndZoom(
    scale: MutableState<Float>,
    offsetX: MutableState<Float>,
    offsetY: MutableState<Float>,
    dragOffsetY: MutableState<Float>,
    slopPx: Float,
    containerSizePx: IntSize,
    onDismissByDrag: () -> Unit,
    onSpringBack: () -> Unit,
) {
    val containerW = containerSizePx.width.toFloat().coerceAtLeast(1f)
    val containerH = containerSizePx.height.toFloat().coerceAtLeast(1f)

    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var classifiedAsDismiss = false
        var accX = 0f
        var accY = 0f
        var lastPinchDistance = -1f
        var lastCentroid = Offset.Zero
        val velocityTracker = VelocityTracker()
        val startZoomed = scale.value > 1.01f

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }

            if (pressed.size >= 2) {
                val (a, b) = pressed
                val distance = (a.position - b.position).getDistance()
                val centroid = Offset((a.position.x + b.position.x) / 2f, (a.position.y + b.position.y) / 2f)
                if (lastPinchDistance > 0f) {
                    val zoomChange = distance / lastPinchDistance
                    val newScale = ViewerZoom.clampScale(scale.value * zoomChange)
                    val pan = centroid - lastCentroid
                    scale.value = newScale
                    offsetX.value = ViewerZoom.clampOffset(offsetX.value + pan.x, containerW, containerW, newScale)
                    offsetY.value = ViewerZoom.clampOffset(offsetY.value + pan.y, containerH, containerH, newScale)
                }
                lastPinchDistance = distance
                lastCentroid = centroid
                pressed.forEach { it.consume() }
            } else if (pressed.size == 1) {
                val change = pressed[0]
                val delta = change.positionChange()
                when {
                    scale.value > 1.01f || startZoomed -> {
                        offsetX.value = ViewerZoom.clampOffset(offsetX.value + delta.x, containerW, containerW, scale.value)
                        offsetY.value = ViewerZoom.clampOffset(offsetY.value + delta.y, containerH, containerH, scale.value)
                        change.consume()
                    }
                    classifiedAsDismiss -> {
                        accY += delta.y
                        dragOffsetY.value = accY.coerceAtLeast(0f)
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                    }
                    lastPinchDistance > 0f -> {
                        // 刚从双指松到单指：只剩缩放跟随，不再重新判定成下滑。
                    }
                    else -> {
                        accX += delta.x
                        accY += delta.y
                        val dist = hypot(accX, accY)
                        if (dist > slopPx) {
                            if (abs(accY) > abs(accX) && accY > 0f) {
                                classifiedAsDismiss = true
                                dragOffsetY.value = accY.coerceAtLeast(0f)
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                            }
                            // 水平占优或向上：不 consume，交还给 HorizontalPager。
                        }
                    }
                }
            }

            if (event.changes.none { it.pressed }) break
        }

        if (classifiedAsDismiss) {
            val velocityY = velocityTracker.calculateVelocity().y
            if (ViewerDismiss.shouldDismiss(accY, containerH, velocityY)) onDismissByDrag() else onSpringBack()
        } else if (lastPinchDistance > 0f && scale.value <= 1.01f) {
            // 捏合缩小回 1x 附近：吸附到干净的静止状态，不留零头缩放/偏移。
            scale.value = 1f
            offsetX.value = 0f
            offsetY.value = 0f
        }
    }
}

/** §4.10：系统"减少动画"开着就跳过共享元素过渡；用动画时长缩放为 0 判定，跟系统设置里的开关是同一个值。 */
private fun isReducedMotionEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
