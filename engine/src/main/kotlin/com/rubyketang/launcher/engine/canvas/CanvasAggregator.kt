package com.rubyketang.launcher.engine.canvas

import com.rubyketang.launcher.model.ScoredTarget

/** P2-1：通知是 top-4 的替换项，不是第五个常驻元素。 */
object CanvasAggregator {
    fun compose(top: List<ScoredTarget>, actionableNotification: ScoredTarget?): List<ScoredTarget> =
        if (actionableNotification == null) top.take(MAX_ITEMS)
        else listOf(actionableNotification) + top.take(MAX_ITEMS - 1)

    private const val MAX_ITEMS = 4
}
