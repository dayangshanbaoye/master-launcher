package com.rubyketang.launcher.data

import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.Target
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 05-product-spec.md §3.2.2/§3.2.3：tag_overrides 从单值改成多归属集合（proto 字段 3 → 15），
 * 并新增 custom_categories（字段 16）。这里只测 [Snapshot.toProto]/[LauncherStateProto.toSnapshot]
 * 这一对纯函数的往返（跟 SnapshotTimingTest 同样不碰 Context/DataStore），确认新字段序列化/
 * 反序列化不丢数据——SnapshotTimingTest 只关心耗时，没断言过这两个字段。
 */
class SnapshotStoreTest {

    private fun target(id: String) = Target(
        id = id,
        kind = Kind.APP,
        label = id,
        launch = LaunchSpec(id),
    )

    private fun baseSnapshot(
        tagOverrides: Map<String, Set<String>> = emptyMap(),
        customCategories: List<String> = emptyList(),
    ) = Snapshot(
        targets = listOf(target("app://a"), target("app://b")),
        usage = emptyMap(),
        tagOverrides = tagOverrides,
        gestures = emptyMap(),
        pins = emptySet(),
        userAliases = emptyMap(),
        customCategories = customCategories,
    )

    @Test
    fun `多归属 tagOverrides 往返不丢数据也不丢顺序内的成员`() {
        val snapshot = baseSnapshot(
            tagOverrides = mapOf(
                "app://a" to setOf("阅读", "工具"),
                "app://b" to setOf("未分类"),
            ),
        )

        val restored = roundTrip(snapshot)

        assertEquals(setOf("阅读", "工具"), restored.tagOverrides["app://a"])
        assertEquals(setOf("未分类"), restored.tagOverrides["app://b"])
    }

    @Test
    fun `单条目多个分类 往返后仍是同一集合 不会被拆或去重错`() {
        val snapshot = baseSnapshot(tagOverrides = mapOf("app://a" to setOf("阅读", "工具", "出行")))

        val restored = roundTrip(snapshot)

        assertEquals(setOf("阅读", "工具", "出行"), restored.tagOverrides["app://a"])
    }

    @Test
    fun `没有覆盖记录时 往返后是空表 不是缺字段报错`() {
        val restored = roundTrip(baseSnapshot())
        assertEquals(emptyMap(), restored.tagOverrides)
    }

    @Test
    fun `自定义分类列表往返保序`() {
        val snapshot = baseSnapshot(customCategories = listOf("追剧", "健身", "副业"))

        val restored = roundTrip(snapshot)

        assertEquals(listOf("追剧", "健身", "副业"), restored.customCategories)
    }

    @Test
    fun `没有自定义分类时 往返后是空列表`() {
        val restored = roundTrip(baseSnapshot())
        assertEquals(emptyList(), restored.customCategories)
    }

    private fun roundTrip(snapshot: Snapshot): Snapshot =
        com.rubyketang.launcher.data.proto.LauncherStateProto.parseFrom(snapshot.toProto().toByteArray()).toSnapshot()
}
