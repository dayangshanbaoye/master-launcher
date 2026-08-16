package com.rubyketang.launcher.ui.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rubyketang.launcher.LauncherState
import com.rubyketang.launcher.LauncherSurface
import com.rubyketang.launcher.resolver.Query
import com.rubyketang.launcher.speech.SpeechEngine
import com.rubyketang.launcher.ui.AppIcon
import com.rubyketang.launcher.ui.TargetContextMenu
import com.rubyketang.launcher.ui.theme.Dimens
import com.rubyketang.launcher.ui.theme.LocalUiScale
import com.rubyketang.launcher.ui.theme.Palette
import com.rubyketang.launcher.ui.theme.Type
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.sample

/**
 * P0-6 Search：上滑唤起，输入框自动聚焦弹键盘；
 * 结果最多 6 条，每条带匹配原因；回车直接打开第一项。
 *
 * P1-1 语音：🎤 常驻输入框内；onPartialResults 增量驱动查询，每 200ms 刷新；
 * 停顿 600ms 锁定第一项（强调色描边）；语音出汉字走 label 匹配（引擎拼音层已覆盖）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun SearchScreen(state: LauncherState, palette: Palette) {
    val uiScale = LocalUiScale.current
    var text by remember { mutableStateOf("") }
    val version by state.indexVersion.collectAsState()
    val dndVersion by state.dndVersion.collectAsState()
    val voiceRequested by state.voiceSearchRequested.collectAsState()
    val results = remember(text, version, dndVersion) {
        if (text.isBlank()) emptyList() else state.resolve(Query(text = text))
    }
    val focusRequester = remember { FocusRequester() }
    var menuTarget by remember { mutableStateOf<com.rubyketang.launcher.model.Target?>(null) }

    // ---- P1-1 语音 ----
    val context = LocalContext.current
    val speech = remember { SpeechEngine.create(context) }
    var listening by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    val partials = remember { MutableStateFlow<String?>(null) }

    DisposableEffect(speech) {
        speech?.listener = object : SpeechEngine.Listener {
            override fun onPartial(text: String) {
                partials.value = text
            }

            override fun onFinal(text: String) {
                partials.value = text
            }

            override fun onError() {}
            override fun onListeningChanged(l: Boolean) {
                listening = l
            }
        }
        onDispose {
            speech?.listener = null
            speech?.destroy()
        }
    }

    // partial result 每 200ms 刷新一次
    LaunchedEffect(Unit) {
        partials.filterNotNull().sample(200).collect {
            locked = false
            text = it
        }
    }

    // 停顿 600ms 锁定第一项
    LaunchedEffect(text, listening) {
        if (listening && text.isNotBlank()) {
            delay(600)
            locked = true
            speech?.stop()
        }
    }

    val startListening = {
        locked = false
        speech?.start()
        Unit
    }
    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startListening() }

    LaunchedEffect(voiceRequested) {
        if (!voiceRequested) return@LaunchedEffect
        state.consumeVoiceSearchRequest()
        if (speech == null) return@LaunchedEffect
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BackHandler { state.surface.value = LauncherSurface.CANVAS }

    fun open(scored: com.rubyketang.launcher.model.ScoredTarget) {
        state.launch(scored.target)
        state.surface.value = LauncherSurface.CANVAS
    }

    fun openFirst() = results.firstOrNull()?.let { open(it) } ?: Unit

    Box(
        Modifier
            .fillMaxSize()
            .padding(Dimens.ScreenMargin)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { state.goHome() }) }
            // 下滑关闭回 Canvas（来路的反向）
    ) {
        Column(Modifier.fillMaxSize()) {
            // 输入框：0.5dp 边框 + 8dp 圆角；🎤 常驻框内
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(Dimens.Border, palette.fg2, RoundedCornerShape(Dimens.CornerRadius))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            locked = false
                            text = it
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        textStyle = TextStyle(color = palette.fg, fontSize = Type.Item),
                        cursorBrush = SolidColor(palette.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { openFirst() }),
                        decorationBox = { inner ->
                            if (text.isEmpty()) {
                                BasicText("搜索", style = TextStyle(color = palette.fg2, fontSize = Type.Item))
                            }
                            inner()
                        },
                    )
                }
                if (listening) {
                    BasicText(
                        "听写中",
                        Modifier.padding(horizontal = 8.dp),
                        style = TextStyle(color = palette.accent, fontSize = Type.Secondary),
                    )
                }
                if (speech != null) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .border(1.dp, palette.accent, CircleShape)
                            .clickable {
                                if (listening) {
                                    speech.stop()
                                } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    startListening()
                                } else {
                                    audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                    )
                }
            }

            // 结果：最多 6 条（Resolver limit 保证），超出说明排序有问题
            Column(Modifier.padding(top = 20.dp)) {
                results.forEachIndexed { index, scored ->
                    val isLockedFirst = locked && index == 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(Dimens.SearchRowHeight)
                            .then(
                                if (isLockedFirst) {
                                    Modifier.border(Dimens.BorderSelected, palette.accent, RoundedCornerShape(Dimens.CornerRadius))
                                } else {
                                    Modifier
                                }
                            )
                            .combinedClickable(
                                onClick = { open(scored) },
                                onLongClick = { menuTarget = scored.target },
                            )
                            .padding(horizontal = if (isLockedFirst) 8.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = state.icons.icon(scored.target.iconUri)
                        // 图标本身携带含义弱于旁边的文字，交给相邻的 label 承担朗读，这里保持装饰性。
                        AppIcon(
                            icon = icon,
                            contentDescription = null,
                            size = 20.dp * uiScale,
                            borderColor = palette.fg2,
                        )
                        Spacer(Modifier.size(12.dp * uiScale))
                        Column {
                            BasicText(
                                scored.target.label,
                                style = TextStyle(color = palette.fg, fontSize = Type.Item),
                            )
                            BasicText(
                                scored.reason.display,
                                Modifier.padding(top = 3.dp),
                                style = TextStyle(color = palette.fg2, fontSize = Type.Secondary),
                            )
                        }
                    }
                }
            }
        }

        menuTarget?.let { target ->
            TargetContextMenu(state, target, palette) { menuTarget = null }
        }
    }
}
