package com.rubyketang.launcher.engine.showcase

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 05-product-spec.md §4.3："解码按显示尺寸 inSampleSize 降采样，绝不加载原图"。
 * Android 官方推荐算法的纯整数版本，跟 BitmapFactory.Options 解耦方便单测——
 * 真正调用 BitmapFactory 的部分在 app 层 ShowcaseImageLoader，这里只验证倍数计算对不对。
 */
class ImageSamplingTest {

    @Test
    fun `原图小于目标尺寸 不做任何降采样`() {
        assertEquals(1, ImageSampling.inSampleSize(300, 300, 500, 500))
    }

    @Test
    fun `原图刚好等于目标尺寸 不做降采样`() {
        assertEquals(1, ImageSampling.inSampleSize(500, 500, 500, 500))
    }

    @Test
    fun `原图是目标的整 4 倍 降采样 4 倍`() {
        assertEquals(4, ImageSampling.inSampleSize(2000, 2000, 500, 500))
    }

    @Test
    fun `原图是目标的 5 倍 非 2 的幂 保守取 4倍 保证解码结果不小于目标`() {
        // 2500/500=5，取到 inSampleSize=8 会让解码结果 312x312 小于目标 500x500，不满足"结果≥目标"，
        // 所以算法在 4 倍处停下（解码出 625x625），这是官方算法的既定行为，不是本实现的取舍。
        assertEquals(4, ImageSampling.inSampleSize(2500, 2500, 500, 500))
    }

    @Test
    fun `只有一个维度超标 另一维度不满足条件时不降采样`() {
        // 官方算法要求两个维度同时满足 half大于等于req 才继续翻倍——高度 400 本来就小于目标 500，
        // 哪怕宽度 2000 远超标也不降采样，保证解码结果两个维度都不小于目标（避免居中裁剪时露边）。
        assertEquals(1, ImageSampling.inSampleSize(2000, 400, 500, 500))
    }

    @Test
    fun `宽高比不同的目标尺寸 两个维度都满足才继续翻倍`() {
        // 原图 1600x1200，目标 400x400：宽比 4、高比 3，用更严格的高度限制，inSampleSize=2（不是 4）
        assertEquals(2, ImageSampling.inSampleSize(1600, 1200, 400, 400))
    }

    @Test
    fun `非法输入不抛异常 也不死循环 直接返回 1`() {
        assertEquals(1, ImageSampling.inSampleSize(0, 0, 500, 500))
        assertEquals(1, ImageSampling.inSampleSize(-100, 2000, 500, 500))
        assertEquals(1, ImageSampling.inSampleSize(2000, 2000, 0, 0))
    }
}
