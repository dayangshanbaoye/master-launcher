package com.rubyketang.launcher.ui.gesture

import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** P2-4：只挂在 Canvas 的空白区域；达到长按阈值后显示，抬手立即消失。 */
fun Modifier.quickReferenceLongPress(onVisibilityChanged: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    detectTapGestures(
        onPress = {
            var visible = false
            coroutineScope {
                val reveal = launch {
                    delay(viewConfiguration.longPressTimeoutMillis)
                    visible = true
                    onVisibilityChanged(true)
                }
                tryAwaitRelease()
                reveal.cancelAndJoin()
            }
            if (visible) onVisibilityChanged(false)
        }
    )
}
