package com.rubyketang.launcher.engine.visibility

import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.Tag
import com.rubyketang.launcher.model.Target
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagDndRegistryTest {
    private val social = Tag("社交")
    private val target = Target("app://wx/Main", Kind.APP, "微信", tags = setOf(social), launch = LaunchSpec("app://wx/Main"))

    @Test fun `按标签隐藏，到期自动恢复`() {
        val dnd = TagDndRegistry()
        dnd.hide(social, 2_000L)

        assertFalse(dnd.isVisible(target, 1_999L))
        assertTrue(dnd.isVisible(target, 2_000L))
        assertTrue(dnd.snapshot(2_000L).isEmpty())
    }

    @Test fun `快照往返保留未到期的隐藏规则`() {
        val original = TagDndRegistry().apply { hide(social, 2_000L) }
        val restored = TagDndRegistry().apply { restore(original.snapshot(1_000L)) }

        assertFalse(restored.isVisible(target, 1_500L))
    }
}
