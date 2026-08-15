package com.rubyketang.launcher.data

/**
 * P2-3 本地优先合并。
 *
 * 本地明确配置（分类覆盖、手势、别名、免打扰）优先于导入内容；使用记录和置顶取并集。
 */
object SnapshotMerger {
    fun localFirst(local: Snapshot, remote: Snapshot): Snapshot = Snapshot(
        targets = (local.targets + remote.targets).distinctBy { it.id },
        usage = (remote.usage.keys + local.usage.keys).associateWith { id ->
            ((remote.usage[id].orEmpty() + local.usage[id].orEmpty())
                .distinctBy { it.atMillis to it.signalMask }
                .sortedBy { it.atMillis })
        },
        tagOverrides = remote.tagOverrides + local.tagOverrides,
        gestures = remote.gestures + local.gestures,
        pins = remote.pins + local.pins,
        userAliases = (remote.userAliases.keys + local.userAliases.keys).associateWith { id ->
            (remote.userAliases[id].orEmpty() + local.userAliases[id].orEmpty()).distinct()
        },
        dndHiddenUntil = remote.dndHiddenUntil + local.dndHiddenUntil,
        syncEnabled = local.syncEnabled || remote.syncEnabled,
        // §2.3：固定簇是"用户钉死"的本机布局决定，本地优先；推荐簇迟滞状态和排除列表同理，
        // 不该被另一台设备的历史覆盖掉本机正在累计的天数。
        fixedSlots = remote.fixedSlots + local.fixedSlots,
        recommendedSlotState = remote.recommendedSlotState + local.recommendedSlotState,
        recommendationExcludedUntil = remote.recommendationExcludedUntil + local.recommendationExcludedUntil,
        recommendedOccupants = remote.recommendedOccupants + local.recommendedOccupants,
    )
}
