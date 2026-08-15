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
import com.rubyketang.launcher.accessibility.AccessibilityStatus
import com.rubyketang.launcher.accessibility.LauncherAccessibilityService
import com.rubyketang.launcher.data.ActionableNotificationRegistry
import com.rubyketang.launcher.data.AndroidContextProvider
import com.rubyketang.launcher.data.AppSource
import com.rubyketang.launcher.data.IconCache
import com.rubyketang.launcher.data.LauncherPreferences
import com.rubyketang.launcher.data.TwoFingerDownAction
import com.rubyketang.launcher.data.RecentAppsProvider
import com.rubyketang.launcher.data.ShortcutSource
import com.rubyketang.launcher.data.Snapshot
import com.rubyketang.launcher.data.SnapshotMerger
import com.rubyketang.launcher.data.SnapshotStore
import com.rubyketang.launcher.engine.gesture.GestureRegistry
import com.rubyketang.launcher.engine.index.InMemoryIndexStore
import com.rubyketang.launcher.engine.index.IndexVersion
import com.rubyketang.launcher.engine.pinyin.DefaultPinyinEngine
import com.rubyketang.launcher.engine.pinyin.Pinyin4jDict
import com.rubyketang.launcher.engine.rank.InMemoryUsageStore
import com.rubyketang.launcher.engine.rank.PinStore
import com.rubyketang.launcher.engine.rank.RecommendedClusterPolicy
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

/** 05-product-spec.md §2.3：固定簇（用户钉死）+ 推荐簇（引擎按迟滞规则选），各 4 个，null = 空槽。 */
data class CanvasSlots(
    val fixed: List<Target?>,
    val recommended: List<Target?>,
    /** §4.2 冷启动：无使用数据且未授权使用统计的头 7 天，推荐簇整体显示"正在学习你的习惯"占位。 */
    val learning: Boolean,
)

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
    val voiceSearchRequested = MutableStateFlow(false)
    val indexVersion: StateFlow<IndexVersion> = store.observe()

    /** §4.5 无障碍失效自检：开机 + 每次回到 Canvas 刷新一次，速查表读这个渲染灰显状态。 */
    val accessibilityGranted = MutableStateFlow(false)

    // §2.3 8 槽应用区
    private val fixedSlotIds = MutableStateFlow<List<String?>>(List(FIXED_SLOT_COUNT) { null })
    private val recommendedOccupantIds = MutableStateFlow<List<String?>>(List(RECOMMENDED_SLOT_COUNT) { null })
    private val recommendedPolicy = RecommendedClusterPolicy()
    private var recommendationExcludedUntil: Map<String, Long> = emptyMap()
    /** 显式版本号：长按改绑固定/推荐槽这类操作不改 index/dnd/notifications，得自己触发 Canvas 重新取数。 */
    val canvasSlotVersion = MutableStateFlow(0)
    private var firstLaunchAtMillis: Long = System.currentTimeMillis()

    /** §4.2 冷启动：无使用数据、未配置任何固定槽、且没做过引导时才触发，只出现一次。 */
    val onboardingActive = MutableStateFlow(false)

    /** §4.6 默认桌面被重置：确认过是默认桌面之后如果掉了，首页浮一条低干扰提示。 */
    val defaultLauncherChanged = MutableStateFlow(false)

    /** 冷启动：快照先进索引（< 20ms 可交互），再后台从 LauncherApps 全量重建。 */
    suspend fun init() {
        snapshots.read()?.let { snap ->
            restoreSnapshot(snap, preloadTargets = true)
        }
        store.rebuild()
        registerPackageCallback()
        refreshAccessibilityStatus()
        checkDefaultLauncherStatus()
        onboardingActive.value = usage.ids().isEmpty() &&
            fixedSlotIds.value.all { it == null } &&
            !preferences.onboardingDone.value
        persist()
    }

    fun resolve(query: Query): List<ScoredTarget> = resolver.resolve(query)

    fun goHome() {
        surface.value = LauncherSurface.CANVAS
        voiceSearchRequested.value = false
    }

    fun back() {
        // 默认桌面上的返回键不结束 Launcher，也不会产生回首页动画。
        if (surface.value != LauncherSurface.CANVAS) goHome()
    }

    /** §4.5：开机已经查过一次；这个给"每次回到 Canvas"用，CanvasScreen 进入组合时调用。 */
    fun refreshAccessibilityStatus() {
        accessibilityGranted.value = AccessibilityStatus.isEnabled(appContext)
    }

    fun openAccessibilitySettings() = AccessibilityStatus.openSettings(appContext)

    /** 05-product-spec.md §1.2/§1.3 手势路由。 */
    fun handleSurfaceGesture(gestureId: String) {
        when (gestureId) {
            "down" -> if (surface.value == LauncherSurface.CANVAS) {
                voiceSearchRequested.value = false
                surface.value = LauncherSurface.SEARCH
            }
            "right" -> if (surface.value == LauncherSurface.CANVAS) {
                surface.value = LauncherSurface.BROWSE
            } // Browse 内右滑无动作（§1.3），不再复用旧的三路循环
            "left" -> back()
            // 未授权时不绑定任何动作、不弹窗骚扰（§4.5）——不再回退到旧的自制最近任务浮层。
            "up" -> if (surface.value == LauncherSurface.CANVAS && accessibilityGranted.value) {
                LauncherAccessibilityService.openRecents()
            }
            // 就地改绑：通知栏（默认）/ 锁屏 / 留空，见速查表（§1.2）。留空时不需要无障碍也不做任何事。
            "two_finger_down" -> if (accessibilityGranted.value) {
                when (preferences.twoFingerDownAction.value) {
                    TwoFingerDownAction.NOTIFICATIONS -> LauncherAccessibilityService.expandNotifications()
                    TwoFingerDownAction.LOCK_SCREEN -> LauncherAccessibilityService.lockScreen()
                    TwoFingerDownAction.NONE -> Unit
                }
            }
            else -> gestures.targetFor(gestureId)?.let { targetId ->
                store.entry(targetId)?.target?.let(::launch)
            }
        }
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

    /**
     * 05-product-spec.md §2.3：固定簇 4（用户钉死，引擎永不改动）+ 推荐簇 4（引擎按迟滞规则选）。
     * P2-1 通知只替换推荐簇第一个槽的*显示*，不碰迟滞状态——通知消失后原来占着的目标原样回来。
     */
    fun canvasSlots(): CanvasSlots {
        val now = System.currentTimeMillis()
        val fixedIds = fixedSlotIds.value
        val fixedTargets = fixedIds.map { id -> id?.let { store.entry(it)?.target } }

        val allScored = resolve(Query(limit = 500))
        val scoreById = allScored.associate { it.target.id to it.score }
        val fixedIdSet = fixedIds.filterNotNull().toSet()
        val excludedActive = recommendationExcludedUntil.filterValues { it > now }.keys
        var pool = allScored
            .filter { it.target.id !in fixedIdSet && it.target.id !in excludedActive }
            .map { it.target.id to it.score }

        val occupants = recommendedOccupantIds.value
        val newOccupants = MutableList<String?>(RECOMMENDED_SLOT_COUNT) { null }
        for (slot in 0 until RECOMMENDED_SLOT_COUNT) {
            // 在位者被卸载了、或者被长按"钉住"转进了固定簇，都当空槽立刻补位——不等 3 天迟滞去
            // "淘汰"一个已经不存在/已经在固定簇重复显示了的 app。
            val occupant = occupants.getOrNull(slot)?.takeIf { it !in fixedIdSet && store.entry(it) != null }
            val occupantScore = occupant?.let { scoreById[it] } ?: 0f
            val resolved = recommendedPolicy.resolve(slot, occupant, occupantScore, pool, now)
            newOccupants[slot] = resolved
            pool = pool.filter { it.first != resolved } // 摘掉，避免其它槽重复选中同一个
        }
        if (newOccupants != occupants) {
            recommendedOccupantIds.value = newOccupants
            persist()
        }

        var recommendedTargets = newOccupants.map { id -> id?.let { store.entry(it)?.target } }
        ActionableNotificationRegistry.entries.value.firstOrNull()?.let { notification ->
            recommendedTargets = listOf(notification.target) + recommendedTargets.drop(1)
        }

        val learning = usage.ids().isEmpty() && !usageAccessGranted() &&
            (now - firstLaunchAtMillis) < COLD_START_LEARNING_WINDOW_MILLIS

        return CanvasSlots(fixedTargets, recommendedTargets, learning)
    }

    /** 长按"钉住"全满时挑替换目标用；不跑推荐簇计算，比 [canvasSlots] 轻。 */
    fun fixedSlotTargets(): List<Target?> = fixedSlotIds.value.map { id -> id?.let { store.entry(it)?.target } }

    /** 长按固定槽"更换"/空槽点击：直接设置该槽内容，null 表示清空。 */
    fun setFixedSlot(index: Int, targetId: String?) {
        if (index !in 0 until FIXED_SLOT_COUNT) return
        val updated = fixedSlotIds.value.toMutableList()
        while (updated.size < FIXED_SLOT_COUNT) updated.add(null)
        updated[index] = targetId
        fixedSlotIds.value = updated
        canvasSlotVersion.value += 1
        persist()
    }

    fun isFixedSlotFull(): Boolean = fixedSlotIds.value.take(FIXED_SLOT_COUNT).all { it != null }

    /** 长按推荐槽"钉住"：有空固定槽直接放进去返回 true；全满返回 false，调用方要弹出选择替换哪个。 */
    fun pinRecommendedToFixed(targetId: String): Boolean {
        val emptyIndex = (0 until FIXED_SLOT_COUNT).firstOrNull { fixedSlotIds.value.getOrNull(it) == null }
            ?: return false
        setFixedSlot(emptyIndex, targetId)
        return true
    }

    /** 长按推荐槽"排除"：30 天内不再推荐；如果它正占着某个推荐槽，那个槽立刻清空重选。 */
    fun excludeFromRecommendations(targetId: String) {
        recommendationExcludedUntil = recommendationExcludedUntil + (targetId to System.currentTimeMillis() + RECOMMENDATION_EXCLUDE_MILLIS)
        recommendedOccupantIds.value = recommendedOccupantIds.value.map { if (it == targetId) null else it }
        canvasSlotVersion.value += 1
        persist()
    }

    /** §4.2 冷启动引导候选：优先用使用统计预填；没有权限就退回已安装应用按名称排序——
     *  这只是"给用户挑"的展示顺序，绝不是往 8 槽里自动填充随机/字母序内容。 */
    fun onboardingCandidates(): List<Target> {
        val fromUsage = recentTargets(limit = 20)
        if (fromUsage.isNotEmpty()) return fromUsage
        return store.all()
            .filter { it.launch.uri.startsWith("app://") }
            .sortedBy { it.label }
            .take(40)
            .toList()
    }

    /** 引导页选中的（最多 4 个）直接钉进固定簇。 */
    fun completeOnboarding(selectedIds: List<String>) {
        selectedIds.take(FIXED_SLOT_COUNT).forEachIndexed { i, id -> setFixedSlot(i, id) }
        preferences.setOnboardingDone(true)
        onboardingActive.value = false
    }

    fun skipOnboarding() {
        preferences.setOnboardingDone(true)
        onboardingActive.value = false
    }

    /** §4.6：开机 + 每次回到 Canvas 检查一次当前默认桌面是不是还是本应用。 */
    fun checkDefaultLauncherStatus() {
        if (isCurrentlyDefaultLauncher()) {
            preferences.setWasDefaultLauncherConfirmed(true)
            defaultLauncherChanged.value = false
            return
        }
        val wasConfirmed = preferences.wasDefaultLauncherConfirmed.value
        val dismissedRecently = System.currentTimeMillis() - preferences.defaultLauncherBannerDismissedAt.value < SEVEN_DAYS_MILLIS
        defaultLauncherChanged.value = wasConfirmed && !dismissedRecently
    }

    fun dismissDefaultLauncherBanner() {
        preferences.setDefaultLauncherBannerDismissedAt(System.currentTimeMillis())
        defaultLauncherChanged.value = false
    }

    private fun isCurrentlyDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = appContext.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == appContext.packageName
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

    fun labelOf(targetId: String): String? = store.entry(targetId)?.target?.label

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
        return Snapshot(
            targets = store.all().toList(),
            usage = usage.snapshot(),
            tagOverrides = tagResolver.overrides(),
            gestures = gestures.bindings(),
            pins = pinned.value,
            userAliases = userAliases.value,
            dndHiddenUntil = dnd.snapshot(System.currentTimeMillis()),
            syncEnabled = syncEnabled,
            fixedSlots = fixedSlotIds.value.withIndex()
                .filter { it.value != null }.associate { it.index to it.value!! },
            recommendedSlotState = recommendedPolicy.snapshot(),
            recommendationExcludedUntil = recommendationExcludedUntil,
            recommendedOccupants = recommendedOccupantIds.value.withIndex()
                .filter { it.value != null }.associate { it.index to it.value!! },
        )
    }

    private suspend fun restoreSnapshot(snapshot: Snapshot, preloadTargets: Boolean) {
        tagResolver.restore(snapshot.tagOverrides)
        usage.restore(snapshot.usage)
        gestures.restore(snapshot.gestures)
        dnd.restore(snapshot.dndHiddenUntil)
        pinned.value = snapshot.pins
        userAliases.value = snapshot.userAliases
        syncEnabled = snapshot.syncEnabled
        if (preloadTargets && snapshot.targets.isNotEmpty()) store.upsert(snapshot.targets)
        fixedSlotIds.value = List(FIXED_SLOT_COUNT) { snapshot.fixedSlots[it] }
        recommendedOccupantIds.value = List(RECOMMENDED_SLOT_COUNT) { snapshot.recommendedOccupants[it] }
        recommendedPolicy.restore(snapshot.recommendedSlotState)
        recommendationExcludedUntil = snapshot.recommendationExcludedUntil
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
        const val FIXED_SLOT_COUNT = 4
        const val RECOMMENDED_SLOT_COUNT = 4
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val RECOMMENDATION_EXCLUDE_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val COLD_START_LEARNING_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
