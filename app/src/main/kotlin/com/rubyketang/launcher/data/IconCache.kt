package com.rubyketang.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * P0-10 图标缓存。Target 里只存 iconUri，Drawable 不进入模型。
 * 单色化不在此处做——渲染时由 UI 用 ColorFilter.tint(次级文本色) 完成。
 */
class IconCache(private val context: Context) {

    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val cache = LruCache<String, IconAsset>(128)

    /**
     * [isMonochrome] 只在系统明确提供 themed 图层时为 true。没有该图层的 app 必须保留
     * 原生图标，不能为了统一色而牺牲辨识度。
     */
    data class IconAsset(
        val bitmap: ImageBitmap,
        val isMonochrome: Boolean,
    )

    fun icon(iconUri: String?): IconAsset? {
        if (iconUri == null) return null
        cache.get(iconUri)?.let { return it }
        val asset = load(iconUri) ?: return null
        cache.put(iconUri, asset)
        return asset
    }

    fun invalidate(iconUriPrefix: String) {
        val snapshot = cache.snapshot()
        for (key in snapshot.keys) {
            if (key.startsWith(iconUriPrefix)) cache.remove(key)
        }
    }

    private fun load(iconUri: String): IconAsset? {
        if (!iconUri.startsWith("icon://")) return null
        val path = iconUri.removePrefix("icon://")
        val pkg = path.substringBefore('/')
        val className = path.substringAfter('/', "").substringBefore('?')
        val profileSerial = path.substringAfter("?profile=", "").toLongOrNull()
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val user = profileSerial?.let { serial ->
            context.getSystemService(UserManager::class.java)
                .let { users -> launcherApps.profiles.firstOrNull { users.getSerialNumberForUser(it) == serial } }
        } ?: Process.myUserHandle()
        val info = launcherApps.getActivityList(pkg, user)
            .firstOrNull { it.componentName.className == className }
            ?: return null
        val systemIcon = info.getIcon(densityDpi)
        // 只有系统明确给出 themed layer 的图标才允许统一染色。多数第三方/OEM 图标没有
        // 这一层，保留原始图标比把不透明背景染成方块更可靠。
        val themed = (systemIcon as? AdaptiveIconDrawable)
            ?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }
            ?.monochrome
        val drawable: Drawable = themed ?: systemIcon
        val size = (48 * context.resources.displayMetrics.density).toInt()
        return IconAsset(
            bitmap = drawable.toBitmap(width = size, height = size).asImageBitmap(),
            isMonochrome = themed != null,
        )
    }
}
