package com.rubyketang.launcher.engine.canvas

import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.MatchReason
import com.rubyketang.launcher.model.ScoreBreakdown
import com.rubyketang.launcher.model.ScoredTarget
import com.rubyketang.launcher.model.Target
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasAggregatorTest {
    private fun item(id: String) = ScoredTarget(
        Target(id, Kind.APP, id, launch = LaunchSpec(id)), 0f,
        MatchReason(""), ScoreBreakdown(0f, 0f, 0f, 0f),
    )

    @Test fun `通知替换第四个 top 条目而不增加常驻数量`() {
        val output = CanvasAggregator.compose(listOf(item("1"), item("2"), item("3"), item("4")), item("notification"))

        assertEquals(listOf("notification", "1", "2", "3"), output.map { it.target.id })
    }
}
