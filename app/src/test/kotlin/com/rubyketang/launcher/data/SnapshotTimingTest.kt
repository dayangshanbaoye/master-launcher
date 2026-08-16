package com.rubyketang.launcher.data

import com.rubyketang.launcher.engine.rank.UsageEvent
import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 05-product-spec.md §4.1 性能预算：索引快照反序列化 < 20ms（500 条目）。
 * 只测 proto ⇄ 模型转换本身（[toProto]/[toSnapshot]，纯 JVM，见 SnapshotStore.kt 里的说明），
 * 不测 DataStore 的磁盘 I/O——那部分需要真机/instrumentation，留到 Wave 6 补 Macrobenchmark。
 */
class SnapshotTimingTest {

    private fun target(i: Int) = Target(
        id = "app://com.generated.$i/Main",
        kind = Kind.APP,
        label = "应用 $i",
        aliases = listOf("app$i", "yingyong$i"),
        launch = LaunchSpec("app://com.generated.$i/Main"),
    )

    private fun bigSnapshot(count: Int): Snapshot {
        val targets = (1..count).map { target(it) }
        val usage = targets.associate { it.id to listOf(UsageEvent(1_700_000_000_000L, 0)) }
        return Snapshot(
            targets = targets,
            usage = usage,
            tagOverrides = targets.take(50).associate { it.id to setOf("工具") },
            gestures = emptyMap(),
            pins = targets.take(4).map { it.id }.toSet(),
            userAliases = targets.take(20).associate { it.id to listOf("我的叫法") },
            dndHiddenUntil = emptyMap(),
            syncEnabled = false,
        )
    }

    @Test
    fun `500 条目快照反序列化小于 20ms`() {
        val snapshot = bigSnapshot(500)
        val bytes = snapshot.toProto().toByteArray()

        // 预热 JIT
        repeat(20) { com.rubyketang.launcher.data.proto.LauncherStateProto.parseFrom(bytes).toSnapshot() }

        val iterations = 100
        val start = System.nanoTime()
        repeat(iterations) {
            com.rubyketang.launcher.data.proto.LauncherStateProto.parseFrom(bytes).toSnapshot()
        }
        val avgMillis = (System.nanoTime() - start) / 1e6 / iterations
        println("500 条目快照反序列化平均耗时: %.3f ms".format(avgMillis))
        assertTrue(avgMillis < 20.0, "反序列化超预算: %.3f ms".format(avgMillis))

        // 大快照下往返结论不变
        val restored = com.rubyketang.launcher.data.proto.LauncherStateProto.parseFrom(bytes).toSnapshot()
        assertEquals(500, restored.targets.size)
        assertEquals(snapshot.pins, restored.pins)
    }
}
