package com.rubyketang.launcher

import android.content.ComponentName
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.os.UserManager
import androidx.core.content.FileProvider
import androidx.core.app.NotificationManagerCompat
import android.provider.Settings
import com.rubyketang.launcher.data.ActionableNotificationRegistry
import com.rubyketang.launcher.data.AndroidContextProvider
import com.rubyketang.launcher.data.AppSource
import com.rubyketang.launcher.data.IconCache
import com.rubyketang.launcher.data.LauncherPreferences
import com.rubyketang.launcher.data.RecentAppsProvider
import com.rubyketang.launcher.data.ShortcutSource
import com.rubyketang.launcher.data.Snapshot
import com.rubyketang.launcher.data.SnapshotMerger
import com.rubyketang.launcher.data.SnapshotStore
import com.rubyketang.launcher.engine.canvas.CanvasAggregator
import com.rubyketang.launcher.engine.gesture.GestureRegistry
import com.rubyketang.launcher.engine.index.InMemoryIndexStore
import com.rubyketang.launcher.engine.index.IndexVersion
import com.rubyketang.launcher.engine.pinyin.DefaultPinyinEngine
import com.rubyketang.launcher.engine.pinyin.Pinyin4jDict
import com.rubyketang.launcher.engine.predict.GestureProposer
import com.rubyketang.launcher.engine.rank.InMemoryUsageStore
import com.rubyketang.launcher.engine.rank.PinStore
import com.rubyketang.launcher.engine.tag.TagResolver
import com.rubyketang.launcher.engine.visibility.TagDndRegistry
import com.rubyketang.launcher.model.MatchReason
import com.rubyketang.launcher.model.ScoreBreakdown
import com.rubyketang.launcher.model.ScoredTarget
import com.rubyketang.launcher.model.Target
import com.rubyketang.launcher.resolver.Query
import com.rubyketang.launcher.resolver.Resolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File

enum class LauncherSurface { CANVAS, SEARCH, BROWSE }

data class CategoryDndAction(val tag: com.rubyketang.launcher.model.Tag, val label: String)
data class GestureHintEntry(val gestureId: String, val label: String)

/**
 * 应用级状态装配：Source → Engine → Resolver 全在这里接线。
 * Surface 只拿这个对象构造 Query、渲染结果。
 */
class LauncherState(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val tagResolver = TagResolver()
    val usage = InMemoryUsageStore()
    val gestures = GestureRegistry()
    val dnd = TagDndRegistry()
    val icons = IconCache(appContext)
    val preferences = LauncherPreferences(appContext)
    private val recentApps = RecentAppsProvider(appContext)
    val proposer = GestureProposer(usage, gestures)

    private val pinned = MutableStateFlow<Set<String>>(emptySet())
    private val pins = PinStore { id -> id in pinned.value }

    /** P1-4 用户别名：targetId → 别名列表，进索引永久生效。 */
    private val userAliases = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    private val appSource = AppSource(appContext, tagResolver) { userAliases.value }
    private val shortcutSource = ShortcutSource(appContext, tagResolver) { userAliases.value }
    private val store = InMemoryIndexStore(listOf(appSource, shortcutSource), DefaultPinyinEngine(Pinyin4jDict()))
    private val snapshots = SnapshotStore(appContext)
    private var syncEnabled = false
    /** DND 到期/变更时触发 Surface 用 Resolver 重新取数。 */
    val dndVersion = MutableStateFlow(0)
    val notificationEntries = ActionableNotificationRegistry.entries

    val resolver = Resolver(
        store = store,
        pinyin = DefaultPinyinEngine(Pinyin4jDict()),
        usage = usage,
        context = AndroidContextProvider(appContext),
        gestures = gestures,
        pins = pins,
        dnd = dnd,
    )

    val surface = MutableStateFlow(LauncherSurface.CANVAS)
    val taskOverviewVisible = MutableStateFlow(false)
    val voiceSearchRequested = MutableStateFlow(false)
    val indexVersion: StateFlow<IndexVersion> = store.observe()

    /** 冷启动：快照先进索引（< 20ms 可交互），再后台从 LauncherApps 全量重建。 */
    suspend fun init() {
        snapshots.read()?.let { snap ->
            restoreSnapshot(snap, preloadTargets = true)
        }
        store.rebuild()
        registerPackageCallback()
        persist()
    }

    fun resolve(query: Query): List<ScoredTarget> = resolver.resolve(query)

    fun goHome() {
        surface.value = LauncherSurface.CANVAS
        taskOverviewVisible.value = false
        voiceSearchRequested.value = false
    }

    fun back() {
        // 默认桌面上的返回键不结束 Launcher，也不会产生回首页动画。
        if (surface.value != LauncherSurface.CANVAS) goHome()
    }

    fun nextSurface() {
        surface.value = when (surface.value) {
            LauncherSurface.CANVAS -> {
                voiceSearchRequested.value = true
                LauncherSurface.SEARCH
            }
            LauncherSurface.SEARCH -> LauncherSurface.BROWSE
            LauncherSurface.BROWSE -> LauncherSurface.CANVAS
        }
    }

    fun handleSurfaceGesture(gestureId: String) {
        when (gestureId) {
            "down" -> if (surface.value == LauncherSurface.CANVAS) {
                voiceSearchRequested.value = false
                surface.value = LauncherSurface.SEARCH
            }
            "up" -> if (surface.value == LauncherSurface.CANVAS) taskOverviewVisible.value = true
            "left" -> back()
            "right" -> nextSurface()
            else -> gestures.targetFor(gestureId)?.let { targetId ->
                store.entry(targetId)?.target?.let(::launch)
            }
        }
    }

    fun dismissTaskOverview() {
        taskOverviewVisible.value = false
    }

    fun consumeVoiceSearchRequest() {
        voiceSearchRequested.value = false
    }

    fun usageAccessGranted(): Boolean = recentApps.isAccessGranted()

    fun recentTargets(limit: Int = 8): List<Target> {
        val byPackage = store.all()
            .filter { it.launch.uri.startsWith("app://") }
            .groupBy { Uri.parse(it.launch.uri).host }
        return recentApps.packageNames(limit).mapNotNull { packageName -> byPackage[packageName]?.firstOrNull() }
    }

    fun requestUsageAccess() {
        appContext.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openHomeSettings() {
        appContext.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** P2-1：通知只替换 top-4 中的一项，Surface 不做条目拼接。 */
    fun canvasEntries(): List<ScoredTarget> {
        val actionable = ActionableNotificationRegistry.entries.value.firstOrNull()?.let { notification ->
            ScoredTarget(
                target = notification.target,
                score = Float.MAX_VALUE,
                reason = MatchReason("可操作通知"),
                breakdown = ScoreBreakdown(0f, 0f, 0f, 0f),
            )
        }
        return CanvasAggregator.compose(resolve(Query(limit = 4)), actionable)
    }

    fun launch(target: Target) {
        resolver.launch(target)
        val spec = target.launch.uri
        try {
            when {
                spec.startsWith("app://") -> {
                    val uri = Uri.parse(spec)
                    val packageName = uri.host ?: return
                    val className = uri.pathSegments.firstOrNull() ?: return
                    val profile = uri.getQueryParameter("profile")?.toLongOrNull()
                    appContext.getSystemService(LauncherApps::class.java).startMainActivity(
                        ComponentName(packageName, className),
                        userFor(profile),
                        null,
                        null,
                    )
                }

                spec.startsWith("shortcut://") -> {
                    val path = spec.removePrefix("shortcut://")
                    appContext.getSystemService(LauncherApps::class.java)
                        .startShortcut(path.substringBefore('/'), path.substringAfter('/'), null, null, Process.myUserHandle())
                }

                spec.startsWith("notification://") -> {
                    ActionableNotificationRegistry.send(spec.removePrefix("notification://"))
                }

                spec.startsWith("http") -> {
                    appContext.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(spec)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        } catch (e: Exception) {
            // 目标刚被卸载等情况，下轮重建会清掉
        }
        persist()
    }

    fun isPinned(id: String): Boolean = id in pinned.value

    fun togglePin(id: String) {
        pinned.value = if (id in pinned.value) pinned.value - id else pinned.value + id
        persist()
    }

    fun overrideTag(targetId: String, category: String) {
        tagResolver.override(targetId, category)
        scope.launch {
            store.rebuild() // tag 打在 Target 上，重挂需要重建
            persist()
        }
    }

    /** P1-4：记住这个叫法。别名写入索引，永久生效。 */
    fun addAlias(targetId: String, alias: String) {
        val cleaned = alias.trim()
        if (cleaned.isEmpty()) return
        userAliases.value = userAliases.value + (targetId to ((userAliases.value[targetId] ?: emptyList()) + cleaned))
        scope.launch {
            store.rebuild() // 别名打在 Target.aliases 上，重建进索引
            persist()
        }
    }

    fun bindGesture(gestureId: String, targetId: String) {
        gestures.bind(gestureId, targetId)
        persist()
    }

    fun unbindGesture(gestureId: String) {
        gestures.unbind(gestureId)
        persist()
    }

    fun gestureBindings(): Map<String, String> = gestures.bindings()

    fun labelOf(targetId: String): String? = store.entry(targetId)?.target?.label

    fun quickReferenceHints(): List<GestureHintEntry> = listOf(
        "corner_tl", "corner_tr", "corner_bl", "corner_br",
    ).mapNotNull { gestureId ->
        gestures.targetFor(gestureId)?.let { targetId ->
            labelOf(targetId)?.let { label -> GestureHintEntry(gestureId, label) }
        }
    }

    /** P2-2：通过已有的条目长按菜单就地启用，不增加设置页。 */
    fun dndActionsFor(target: Target): List<CategoryDndAction> = target.tags.map { tag ->
        if (dnd.isHidden(tag, System.currentTimeMillis())) {
            CategoryDndAction(tag, "恢复 ${tag.name}")
        } else {
            CategoryDndAction(tag, "免打扰 ${tag.name} 1 小时")
        }
    }

    fun toggleDnd(tag: com.rubyketang.launcher.model.Tag) {
        val now = System.currentTimeMillis()
        if (dnd.isHidden(tag, now)) dnd.restore(tag) else dnd.hide(tag, now + DND_ONE_HOUR_MILLIS)
        dndVersion.value += 1
        scheduleDndWakeup()
        persist()
    }

    /** P2-3：用户显式点按后才导出本地快照并打开系统分享，不依赖云端。 */
    fun syncToOtherDevice() {
        scope.launch {
            syncEnabled = true
            val bytes = snapshots.export(currentSnapshot())
            val uri = withContext(Dispatchers.IO) {
                val directory = File(appContext.cacheDir, "sync").apply { mkdirs() }
                File(directory, "master-launcher-sync.pb").apply { writeBytes(bytes) }
            }.let { file -> FileProvider.getUriForFile(appContext, "${appContext.packageName}.sync", file) }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = SYNC_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("Master Launcher 同步", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appContext.startActivity(
                Intent.createChooser(send, "同步到另一台设备").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            persist()
        }
    }

    /** 通知监听是系统受控权限，入口放在就地菜单，不做应用设置页。 */
    fun notificationAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(appContext).contains(appContext.packageName)

    fun requestNotificationAccess() {
        appContext.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** 接收系统分享的快照；冲突由 SnapshotMerger 按“本地优先”解决。 */
    fun importSharedIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != SYNC_MIME_TYPE) return
        @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: return@launch
            val remote = snapshots.import(bytes) ?: return@launch
            restoreSnapshot(SnapshotMerger.localFirst(currentSnapshot(), remote), preloadTargets = true)
            // 导入的索引只用来合并用户数据；立刻按本机 LauncherApps 重建，绝不展示远端未安装 app。
            store.rebuild()
            syncEnabled = true
            persist()
        }
    }

    /** P1-3：当前该浮出的手势提议（无则 null）。 */
    fun pendingProposal(): Target? =
        proposer.proposal(System.currentTimeMillis())?.let { store.entry(it)?.target }

    /** P1-3：接受 → 绑到第一个空着的角滑槽。 */
    fun acceptProposal(targetId: String) {
        val free = listOf("corner_tl", "corner_tr", "corner_bl", "corner_br")
            .firstOrNull { gestures.targetFor(it) == null } ?: return
        gestures.bind(free, targetId)
        proposer.accept(targetId)
        persist()
    }

    fun rejectProposal(targetId: String) {
        proposer.reject(targetId, System.currentTimeMillis())
        persist()
    }

    /** Browse 左栏：有内容的分类（按 TagResolver.ALL 的固定顺序）+ 全部。 */
    fun categories(): List<String> {
        val now = System.currentTimeMillis()
        val present = store.all().filter { dnd.isVisible(it, now) }.flatMap { it.tags }.map { it.name }.toSet()
        return TagResolver.ALL.filter { it in present } + TagResolver.ALL_APPS
    }

    /** Browse"全部"的索引条用：条目的拼音首字母（大写），非字母归 "#"。只读预计算键，不做匹配。 */
    fun initialOf(targetId: String): String {
        val initials = store.entry(targetId)?.keys?.initials ?: return "#"
        val c = initials.firstOrNull() ?: return "#"
        return if (c in 'a'..'z') c.uppercase() else "#"
    }

    private fun persist() {
        scope.launch {
            snapshots.write(currentSnapshot())
        }
    }

    private fun currentSnapshot(): Snapshot {
        val (proposed, rejected) = proposer.snapshot()
        return Snapshot(
            targets = store.all().toList(),
            usage = usage.snapshot(),
            tagOverrides = tagResolver.overrides(),
            gestures = gestures.bindings(),
            pins = pinned.value,
            userAliases = userAliases.value,
            proposalDone = proposed,
            proposalRejectedUntil = rejected,
            dndHiddenUntil = dnd.snapshot(System.currentTimeMillis()),
            syncEnabled = syncEnabled,
        )
    }

    private suspend fun restoreSnapshot(snapshot: Snapshot, preloadTargets: Boolean) {
        tagResolver.restore(snapshot.tagOverrides)
        usage.restore(snapshot.usage)
        gestures.restore(snapshot.gestures)
        dnd.restore(snapshot.dndHiddenUntil)
        proposer.restore(snapshot.proposalDone, snapshot.proposalRejectedUntil)
        pinned.value = snapshot.pins
        userAliases.value = snapshot.userAliases
        syncEnabled = snapshot.syncEnabled
        if (preloadTargets && snapshot.targets.isNotEmpty()) store.upsert(snapshot.targets)
        dndVersion.value += 1
        scheduleDndWakeup()
    }

    private fun scheduleDndWakeup() {
        val now = System.currentTimeMillis()
        val next = dnd.snapshot(now).values.minOrNull() ?: return
        scope.launch {
            delay((next - now).coerceAtLeast(1L))
            dnd.snapshot(System.currentTimeMillis())
            dndVersion.value += 1
            persist()
        }
    }

    /** PACKAGE_ADDED / REMOVED / CHANGED / 快捷方式变化 → 增量更新，不全量重建。 */
    private fun registerPackageCallback() {
        val launcherApps = appContext.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: android.os.UserHandle) {
                scope.launch { store.upsert(appSource.loadPackage(packageName, user)); persist() }
            }

            override fun onPackageChanged(packageName: String, user: android.os.UserHandle) {
                scope.launch {
                    removePackage(packageName, user)
                    store.upsert(appSource.loadPackage(packageName, user))
                    persist()
                }
            }

            override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) {
                scope.launch { removePackage(packageName, user); persist() }
            }

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<android.content.pm.ShortcutInfo>,
                user: android.os.UserHandle,
            ) {
                // 快捷方式集合按包重建：先摘该包旧快捷方式，再全量重挂该包的
                scope.launch {
                    val stale = withContext(Dispatchers.Default) {
                        store.all().filter { it.id.startsWith("shortcut://$packageName/") }.map { it.id }.toList()
                    }
                    stale.forEach { store.remove(it) }
                    store.upsert(shortcutSource.loadPackage(packageName))
                    persist()
                }
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean,
            ) = Unit

            override fun onPackagesUnavailable(
                packageNames: Array<out String>, user: android.os.UserHandle, replacing: Boolean,
            ) = Unit
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    private suspend fun removePackage(packageName: String, user: android.os.UserHandle) {
        val serial = appContext.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        val primarySerial = appContext.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        val ids = withContext(Dispatchers.Default) {
            store.all()
                .filter { target ->
                    val inPackage = target.id.startsWith("app://$packageName/") ||
                        target.id.startsWith("shortcut://$packageName/")
                    val profileMatches = if (serial == primarySerial) {
                        !target.id.contains("?profile=")
                    } else {
                        target.id.endsWith("?profile=$serial")
                    }
                    inPackage && profileMatches
                }
                .map { it.id }
                .toList()
        }
        for (id in ids) {
            store.remove(id)
            icons.invalidate(id.replace("app://", "icon://"))
        }
    }

    private fun userFor(profileSerial: Long?): android.os.UserHandle {
        if (profileSerial == null) return Process.myUserHandle()
        val users = appContext.getSystemService(UserManager::class.java)
        val launcherApps = appContext.getSystemService(LauncherApps::class.java)
        return launcherApps.profiles.firstOrNull { users.getSerialNumberForUser(it) == profileSerial }
            ?: Process.myUserHandle()
    }

    private companion object {
        const val DND_ONE_HOUR_MILLIS = 60L * 60 * 1000
        const val SYNC_MIME_TYPE = "application/vnd.master-launcher.sync"
    }
}
