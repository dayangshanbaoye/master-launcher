package com.rubyketang.launcher.engine.browse

import kotlin.test.Test
import kotlin.test.assertEquals

class CommonPrefixTest {
    @Test fun `单条目没有相邻项时返回零，不崩溃`() {
        assertEquals(0, CommonPrefix.sharedPrefixLength("设置", emptyList()))
    }

    @Test fun `相邻项共享至少两字前缀时返回最长共享长度`() {
        assertEquals(4, CommonPrefix.sharedPrefixLength("多看阅读 HD", listOf("多看阅读")))
    }
}
