package com.rubyketang.launcher.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import com.rubyketang.launcher.data.proto.LauncherStateProto
import com.rubyketang.launcher.data.proto.StringListProto
import com.rubyketang.launcher.data.proto.TargetProto
import com.rubyketang.launcher.data.proto.UsageEventProto
import com.rubyketang.launcher.data.proto.UsageEventsProto
import com.rubyketang.launcher.engine.rank.UsageEvent
import com.rubyketang.launcher.model.Kind
import com.rubyketang.launcher.model.LaunchSpec
import com.rubyketang.launcher.model.Tag
import com.rubyketang.launcher.model.Target
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.io.OutputStream

/** Proto DataStore 快照：索引 + frecency 事件（含信号）+ 覆盖/别名/手势/pin/提议状态。 */

data class Snapshot(
    val targets: List<Target>,
    val usage: Map<String, List<UsageEvent>>,
    val tagOverrides: Map<String, String>,
    val gestures: Map<String, String>,
    val pins: Set<String>,
    val userAliases: Map<String, List<String>>,
    val dndHiddenUntil: Map<String, Long> = emptyMap(),
    val syncEnabled: Boolean = false,
)

private object LauncherStateSerializer : Serializer<LauncherStateProto> {
    override val defaultValue: LauncherStateProto = LauncherStateProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): LauncherStateProto = try {
        LauncherStateProto.parseFrom(input)
    } catch (e: InvalidProtocolBufferException) {
        throw CorruptionException("launcher_state 快照损坏", e)
    }

    override suspend fun writeTo(t: LauncherStateProto, output: OutputStream) = t.writeTo(output)
}

private val Context.launcherStateStore by dataStore(
    fileName = "launcher_state.pb",
    serializer = LauncherStateSerializer,
)

class SnapshotStore(private val context: Context) {

    /** 冷启动读取。损坏/不存在返回 null，上层走全量重建。 */
    suspend fun read(): Snapshot? = try {
        context.launcherStateStore.data.first().toSnapshot()
    } catch (e: Exception) {
        null
    }

    suspend fun write(snapshot: Snapshot) {
        context.launcherStateStore.updateData { snapshot.toProto() }
    }

    /** P2-3：同步交换的是已有的本地快照，不需要云端数据库。 */
    fun export(snapshot: Snapshot): ByteArray = snapshot.toProto().toByteArray()

    fun import(bytes: ByteArray): Snapshot? = try {
        LauncherStateProto.parseFrom(bytes).toSnapshot()
    } catch (_: InvalidProtocolBufferException) {
        null
    }
}

/**
 * Proto ⇄ 模型转换，跟 [SnapshotStore] 拆开成顶层 internal 函数——这部分是纯数据转换，
 * 不碰 [Context]，拆出来才能在纯 JVM 测试里量"快照反序列化 < 20ms"（05-product-spec.md §4.1），
 * 不用为了一个计时测试去起 Robolectric。
 */
internal fun LauncherStateProto.toSnapshot() = Snapshot(
    targets = targetsList.map { it.toTarget() },
    usage = usageMap.mapValues { (_, v) -> v.eventsList.map { UsageEvent(it.at, it.signals) } },
    tagOverrides = tagOverridesMap,
    gestures = gesturesMap,
    pins = pinsList.toSet(),
    userAliases = userAliasesMap.mapValues { it.value.valuesList },
    dndHiddenUntil = dndHiddenUntilMap,
    syncEnabled = syncEnabled,
)

internal fun TargetProto.toTarget() = Target(
    id = id,
    kind = Kind.valueOf(kind),
    label = label,
    aliases = aliasesList,
    tags = tagsList.map { Tag(it) }.toSet(),
    iconUri = iconUri.ifEmpty { null },
    launch = LaunchSpec(launchUri),
)

internal fun Snapshot.toProto(): LauncherStateProto {
    val builder = LauncherStateProto.newBuilder()
    targets.forEach { t ->
        builder.addTargets(
            TargetProto.newBuilder()
                .setId(t.id)
                .setKind(t.kind.name)
                .setLabel(t.label)
                .addAllAliases(t.aliases)
                .addAllTags(t.tags.map { it.name })
                .setIconUri(t.iconUri ?: "")
                .setLaunchUri(t.launch.uri)
        )
    }
    usage.forEach { (id, events) ->
        builder.putUsage(
            id,
            UsageEventsProto.newBuilder()
                .addAllEvents(events.map { UsageEventProto.newBuilder().setAt(it.atMillis).setSignals(it.signalMask).build() })
                .build()
        )
    }
    builder.putAllTagOverrides(tagOverrides)
    builder.putAllGestures(gestures)
    builder.addAllPins(pins)
    userAliases.forEach { (id, aliases) ->
        builder.putUserAliases(id, StringListProto.newBuilder().addAllValues(aliases).build())
    }
    builder.putAllDndHiddenUntil(dndHiddenUntil)
    builder.syncEnabled = syncEnabled
    return builder.build()
}
