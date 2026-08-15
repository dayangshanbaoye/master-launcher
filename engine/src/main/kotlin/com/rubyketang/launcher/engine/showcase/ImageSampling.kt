package com.rubyketang.launcher.engine.showcase

/**
 * 05-product-spec.md §4.3："解码按显示尺寸 inSampleSize 降采样，绝不加载原图"。
 * Android 官方推荐算法的纯整数版本，不碰 BitmapFactory——真正调用 BitmapFactory.Options
 * 的部分在 app 层 ShowcaseImageLoader，这里只做倍数计算，方便在 JVM 单测里钉死边界行为。
 */
object ImageSampling {
    /**
     * 两个维度必须同时满足 `half / inSampleSize >= req` 才继续翻倍——保证解码结果两个维度
     * 都不小于目标尺寸（展示区图片按 centerCrop 显示，任一维度小于目标都会露边）。
     */
    fun inSampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
