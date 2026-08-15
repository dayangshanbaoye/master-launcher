package com.rubyketang.launcher.resolver

import com.rubyketang.launcher.model.Tag

/**
 * 三个 Surface（Search / Browse / Gesture）构造的统一查询。
 * 差别只在填哪个字段，解析路径只有 Resolver 一条。
 */
data class Query(
    val text: String? = null,       // 来自 Search（键盘或语音 onPartialResults）
    val tag: Tag? = null,           // 来自 Browse
    val gestureId: String? = null,  // 来自 Gesture
    val limit: Int = 6,
)
