package com.rubyketang.launcher.data

/**
 * P2-3 本地优先合并。
 *
 * 本地明确配置（分类覆盖、手势、别名、免打扰）优先于导入内容；使用记录、置顶和已提议状态取并集。
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
        proposalDone = remote.proposalDone + local.proposalDone,
        proposalRejectedUntil = remote.proposalRejectedUntil + local.proposalRejectedUntil,
        dndHiddenUntil = remote.dndHiddenUntil + local.dndHiddenUntil,
        syncEnabled = local.syncEnabled || remote.syncEnabled,
    )
}
