package com.rubyketang.launcher.engine.pinyin

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/** 单字拼音词典。抽象出来便于测试注入小词典。 */
interface PinyinDict {
    fun isHan(c: Char): Boolean

    /**
     * 该字最高频读音，小写无音调；查不到返回 null。
     * 多音字取词典中的高频读音，不做上下文推断。
     */
    fun pinyin(c: Char): String?
}

/** 基于 pinyin4j（纯 Java）的实现，覆盖基本汉字区。 */
class Pinyin4jDict : PinyinDict {

    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    override fun isHan(c: Char): Boolean = c in '一'..'鿿'

    override fun pinyin(c: Char): String? =
        PinyinHelper.toHanyuPinyinStringArray(c, format)?.firstOrNull()
}
