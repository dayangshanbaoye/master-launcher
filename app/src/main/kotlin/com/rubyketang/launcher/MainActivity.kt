package com.rubyketang.launcher

import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.role.RoleManager
import android.os.Build
import android.Manifest
import android.bluetooth.BluetoothAdapter
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.rubyketang.launcher.ui.browse.BrowseScreen
import com.rubyketang.launcher.ui.canvas.CanvasScreen
import com.rubyketang.launcher.ui.search.SearchScreen
import com.rubyketang.launcher.ui.gesture.launcherGestures
import com.rubyketang.launcher.ui.showcase.ShowcaseViewerOverlay
import com.rubyketang.launcher.ui.showcase.ShowcaseViewerRequest
import com.rubyketang.launcher.ui.theme.LocalUiScale
import com.rubyketang.launcher.ui.theme.motionSpec
import com.rubyketang.launcher.ui.theme.warmPalette
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var state: LauncherState
    private val requestHomeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val requestBluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestBluetoothEnable()
    }

    /**
     * 05-product-spec.md §2.5 海报模式"每次解锁"切换时机——启动器不是系统进程，只有前台存活时才
     * 能收到 ACTION_USER_PRESENT，动态注册（不进 Manifest）就够用：本应用作为默认桌面几乎总在
     * 前台或刚从后台切回，跟无障碍失效自检（§4.5）同样的覆盖率取舍。
     */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            state.onScreenUnlocked()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = LauncherState(applicationContext)
        lifecycleScope.launch {
            state.init()
            state.onScreenUnlocked() // 冷启动首次进 Canvas 等价于一次"看到展示区"，建立轮换基准
            state.importSharedIntent(intent)
        }
        setContent {
            LauncherRoot(state, onEnableBluetooth = ::requestBluetoothEnable, onSetDefaultLauncher = ::requestHomeRoleAlways)
        }
        requestHomeRoleIfNeeded()
        hideNavigationBar()
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(unlockReceiver) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        state.importSharedIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        hideNavigationBar()
        // §4.5：授权/撤销发生在系统设置里，onResume 是唯一保证覆盖"去系统设置改完权限再切回来"
        // 这条路径的时机——单靠 Canvas 重新进入组合的 LaunchedEffect 覆盖不到应用一直停留在
        // Canvas、只是切到后台又切回来的情况。
        state.refreshAccessibilityStatus()
        // §4.6：默认桌面在系统设置里被换掉也是同一条"切到后台再切回"路径，一并在这里查。
        state.checkDefaultLauncherStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    /**
     * 默认 Launcher 是快捷方式完整可见、增量回调可靠的系统前提。
     * 使用系统角色弹窗，不引入应用设置页；Android 9 及以下由系统 HOME 选择器处理。
     */
    private fun requestHomeRoleIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roles = getSystemService(RoleManager::class.java)
        if (HomeRolePolicy.shouldRequest(
                isAvailable = roles.isRoleAvailable(RoleManager.ROLE_HOME),
                isHeld = roles.isRoleHeld(RoleManager.ROLE_HOME),
            )
        ) {
            requestHomeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    /** 05-product-spec.md §2.4"设为默认桌面"：设置页里用户主动点，跳过 shouldRequest 的节流判断。 */
    private fun requestHomeRoleAlways() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            state.openHomeSettings()
            return
        }
        val roles = getSystemService(RoleManager::class.java)
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            requestHomeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    private fun requestBluetoothEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    private fun hideNavigationBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}

@Composable
fun LauncherRoot(state: LauncherState, onEnableBluetooth: () -> Unit, onSetDefaultLauncher: () -> Unit) {
    val palette = warmPalette(isSystemInDarkTheme())
    val surface by state.surface.collectAsState()
    val scale by state.preferences.fontScale.collectAsState()
    // 05-product-spec.md §2.5 全屏查看器打开时独占触摸：下面这层 BackHandler 优先处理关闭，
    // 全局手势层和双击回首页那层直接不挂载（见下方 if (viewerRequest == null)），不指望"抢赢"。
    var viewerRequest by remember { mutableStateOf<ShowcaseViewerRequest?>(null) }
    BackHandler { if (viewerRequest != null) viewerRequest = null else state.back() }
    CompositionLocalProvider(LocalUiScale provides scale) {
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .safeDrawingPadding()
            .let { if (viewerRequest == null) it.launcherGestures(state::handleSurfaceGesture) else it },
    ) {
        if (viewerRequest == null) {
            // Sheet 上滑跟手：统一 spring，不用 tween
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { state.goHome() }) }
            )
        }
        AnimatedContent(
            targetState = surface,
            transitionSpec = {
                if (targetState == LauncherSurface.CANVAS) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val slideIn = slideInVertically(motionSpec()) { it }
                    val slideOut = slideOutVertically(motionSpec()) { it }
                    slideIn.togetherWith(slideOut)
                }
            },
            label = "surface",
        ) { current ->
            when (current) {
                LauncherSurface.CANVAS -> CanvasScreen(
                    state, palette, onEnableBluetooth, onSetDefaultLauncher,
                    onOpenShowcaseViewer = { viewerRequest = it },
                )
                LauncherSurface.SEARCH -> SearchScreen(state, palette)
                LauncherSurface.BROWSE -> BrowseScreen(state, palette)
            }
        }

        viewerRequest?.let { request ->
            ShowcaseViewerOverlay(
                request = request,
                imageLoader = state.showcaseImages,
                onDismiss = { viewerRequest = null },
            )
        }
    }
    }
}
