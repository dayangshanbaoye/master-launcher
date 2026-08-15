package com.rubyketang.launcher.data

import com.rubyketang.launcher.engine.rank.UsageEvent
import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.Tag
import com.rubyketang.launcher.model.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotMergerTest {
    private val localTarget = Target("app://local/Main", Kind.APP, "本机", tags = setOf(Tag("工具")), launch = LaunchSpec("app://local/Main"))
    private val remoteTarget = Target("app://remote/Main", Kind.APP, "远端", tags = setOf(Tag("阅读")), launch = LaunchSpec("app://remote/Main"))

    @Test fun `P2-3 本地配置优先，使用记录与内容取并集`() {
        val local = Snapshot(
            targets = listOf(localTarget), usage = mapOf(localTarget.id to listOf(UsageEvent(2L, 0))),
            tagOverrides = mapOf(localTarget.id to "工具"), gestures = mapOf("corner_tl" to localTarget.id),
            pins = setOf(localTarget.id), userAliases = mapOf(localTarget.id to listOf("我的")),
            proposalDone = emptySet(), proposalRejectedUntil = emptyMap(), dndHiddenUntil = mapOf("工具" to 9L),
            syncEnabled = true,
        )
        val remote = Snapshot(
            targets = listOf(remoteTarget), usage = mapOf(localTarget.id to listOf(UsageEvent(1L, 0))),
            tagOverrides = mapOf(localTarget.id to "阅读"), gestures = mapOf("corner_tl" to remoteTarget.id),
            pins = setOf(remoteTarget.id), userAliases = mapOf(localTarget.id to listOf("远端叫法")),
            proposalDone = setOf(remoteTarget.id), proposalRejectedUntil = emptyMap(),
        )

        val merged = SnapshotMerger.localFirst(local, remote)

        assertEquals("工具", merged.tagOverrides[localTarget.id])
        assertEquals(localTarget.id, merged.gestures["corner_tl"])
        assertEquals(listOf("远端叫法", "我的"), merged.userAliases[localTarget.id])
        assertEquals(listOf(1L, 2L), merged.usage[localTarget.id]?.map { it.atMillis })
        assertEquals(setOf(localTarget.id, remoteTarget.id), merged.pins)
        assertTrue(merged.syncEnabled)
    }
}
