package com.rubyketang.launcher.data

import com.rubyketang.launcher.engine.tag.TagResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LaunchableActivityMapperTest {
    @Test fun `不截断，137 个可启动 Activity 全部进入 Target 索引`() {
        val mapper = LaunchableActivityMapper(TagResolver()) { emptyMap() }
        val activities = (1..137).map { index ->
            LaunchableActivity(
                packageName = "com.example.$index",
                className = "com.example.$index.MainActivity",
                label = "应用$index",
                applicationCategory = -1,
            )
        }

        val targets = mapper.map(activities)

        assertEquals(137, targets.size)
        assertEquals(137, targets.map { it.id }.distinct().size)
        assertTrue(targets.all { it.id.startsWith("app://com.example.") })
    }

    @Test fun `主用户与双开 profile 的同一应用保留两个独立入口`() {
        val mapper = LaunchableActivityMapper(TagResolver()) { emptyMap() }
        val targets = mapper.map(
            listOf(
                LaunchableActivity("com.tencent.mm", "com.tencent.mm.ui.LauncherUI", "微信", -1),
                LaunchableActivity("com.tencent.mm", "com.tencent.mm.ui.LauncherUI", "微信（双开）", -1, profileSerial = 12L),
            )
        )

        assertEquals(2, targets.size)
        assertEquals(2, targets.map { it.id }.distinct().size)
        assertTrue(targets.any { it.id.endsWith("?profile=12") })
        assertTrue(targets.any { it.iconUri?.endsWith("?profile=12") == true })
    }
}
