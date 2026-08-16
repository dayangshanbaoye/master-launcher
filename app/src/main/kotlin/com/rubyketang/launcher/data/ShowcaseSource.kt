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

    /**
     * §4.5 权限矩阵"读取媒体"一行：授权在系统设置里可能被用户手动撤销，或者换机场景下导入的快照
     * 带着一个本机从没授权过的 URI——这两种情况下 [photos] 会静默返回空列表（DocumentFile 内部
     * 吞掉 SecurityException），跟"文件夹是空的"这种正常情况长得一样，UI 没法区分，也就没法把
     * "未授权"的引导重新亮出来。这里直接查 persistedUriPermissions 现存清单，给调用方一个明确信号。
     */
    fun hasAccess(folderUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == folderUri && it.isReadPermission }
}
