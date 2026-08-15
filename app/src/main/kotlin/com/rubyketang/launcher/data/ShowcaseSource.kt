package com.rubyketang.launcher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 05-product-spec.md §2.5 展示区图片来源：用户通过 SAF 选一个本地文件夹，"不集成任何 API"，
 * 不申请 READ_MEDIA_IMAGES——SAF 树 URI 本身就是授权，符合"核心三入口零权限依赖"之外
 * 展示区这类可选功能"未授权时降级"的同一套克制原则（§4.5 读取媒体一行）。
 *
 * 按文件名排序保证跨次启动顺序稳定——[com.rubyketang.launcher.engine.showcase.ShowcaseRotationPolicy]
 * 存的是"第几张"这个索引，如果每次启动列出的顺序都不一样，索引就对不上原来那张图。
 */
class ShowcaseSource(private val context: Context) {

    suspend fun photos(folderUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val root = runCatching { DocumentFile.fromTreeUri(context, folderUri) }.getOrNull() ?: return@withContext emptyList()
        if (!root.isDirectory) return@withContext emptyList()
        root.listFiles()
            .filter { it.isFile && it.type?.startsWith("image/") == true }
            .sortedBy { it.name ?: "" }
            .map { it.uri }
    }

    /** 选完文件夹后调用一次，让权限跨越重启/关机存活——不然下次冷启动 SAF 授权就失效了。 */
    fun persistPermission(folderUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                folderUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
    }
}
