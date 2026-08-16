package com.rubyketang.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Process
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * P0-10 图标缓存。Target 里只存 iconUri，Drawable 不进入模型。
 *
 * style-guide.html §06：图标统一保留原色渲染，不再取系统"主题单色图层"、也不在这里做
 * 任何染色——降饱和/亮度罩这些展示层处理交给 [com.rubyketang.launcher.ui.AppIcon]，
 * 这里只负责把原始图标解出来，外加算一次平均亮度供上层判断要不要叠灰罩。
 */
class IconCache(private val context: Context) {

    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val cache = LruCache<String, IconAsset>(128)

    data class IconAsset(
        val bitmap: ImageBitmap,
        /** 0..1，采样后的平均亮度，供 UI 层判断是否需要叠灰罩（style-guide.html §06）。 */
        val brightness: Float,
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
        val size = (48 * context.resources.displayMetrics.density).toInt()
        val bitmap = info.getIcon(densityDpi).toBitmap(width = size, height = size)
        return IconAsset(
            bitmap = bitmap.asImageBitmap(),
            brightness = averageLuminance(bitmap),
        )
    }

    /** 缩到 8×8 采样算平均亮度（0..1），忽略接近全透明的像素。 */
    private fun averageLuminance(bitmap: Bitmap): Float {
        val sample = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        var total = 0f
        var count = 0
        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val pixel = sample.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 32) continue
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                total += (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                count++
            }
        }
        sample.recycle()
        return if (count > 0) total / count else 0f
    }
}
