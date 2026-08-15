package com.rubyketang.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.rubyketang.launcher.engine.showcase.ImageSampling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 05-product-spec.md §4.3 展示区资源管理：
 * - 解码按显示尺寸 [ImageSampling] 降采样，绝不加载原图
 * - 磁盘缓存已解码缩略图，冷启动直接读，不用重新解码原图
 * - 内存驻留有预算——海报模式只需当前+预取下一张两张，相册墙要同时驻留一屏全部缩略图，
 *   用字节数（而不是 IconCache 那种固定条目数 LruCache）做上限，因为缩略图尺寸差异远比图标大，
 *   条目数控不住真实内存占用
 * - 全屏查看器专用的高分辨率解码不进磁盘缓存——用完就该被回收，不值得占盘空间
 */
class ShowcaseImageLoader(private val context: Context) {

    private val memoryCache = object : LruCache<String, ImageBitmap>(maxMemoryBytes(context)) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * BYTES_PER_PIXEL
    }

    private val diskCacheDir = File(context.cacheDir, "showcase_thumbs")

    /** 缩略图：内存 → 磁盘 → 解码降采样并写盘，三级依次回退。 */
    suspend fun thumbnail(uri: Uri, targetWidthPx: Int, targetHeightPx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val key = cacheKey(uri, targetWidthPx, targetHeightPx)
            memoryCache.get(key)?.let { return@withContext it }

            diskCacheDir.mkdirs()
            val diskFile = File(diskCacheDir, key)
            val bitmap = if (diskFile.exists()) {
                runCatching { BitmapFactory.decodeFile(diskFile.path) }.getOrNull()
            } else {
                decodeSampled(uri, targetWidthPx, targetHeightPx)?.also { decoded ->
                    runCatching {
                        diskFile.outputStream().use { decoded.compress(Bitmap.CompressFormat.JPEG, DISK_CACHE_QUALITY, it) }
                    }
                }
            } ?: return@withContext null

            bitmap.asImageBitmap().also { memoryCache.put(key, it) }
        }

    /** §4.3："进入时才解码高分辨率版本，退出立即释放"——不放进 [memoryCache]，调用方持有的引用一失效就该被回收。 */
    suspend fun fullRes(uri: Uri, maxDimensionPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        decodeSampled(uri, maxDimensionPx, maxDimensionPx)?.asImageBitmap()
    }

    /** 文件夹换了/照片被删，旧缩略图的磁盘缓存该清掉，不然占盘只增不减。 */
    fun clearDiskCache() {
        diskCacheDir.deleteRecursively()
    }

    private fun decodeSampled(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInput(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageSampling.inSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        }
        return openInput(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun openInput(uri: Uri) = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()

    private fun cacheKey(uri: Uri, w: Int, h: Int): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$uri:$w:$h".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4 // ARGB_8888
        const val DISK_CACHE_QUALITY = 90

        /** 用可用内存的 1/16 做展示区图片预算——相册墙一屏要同时驻留 6-9 张缩略图，固定张数上限控不住真实字节占用。 */
        fun maxMemoryBytes(context: Context): Int {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            return (activityManager.memoryClass * 1024 * 1024) / 16
        }
    }
}
