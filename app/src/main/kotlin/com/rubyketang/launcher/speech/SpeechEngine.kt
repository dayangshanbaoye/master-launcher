package com.rubyketang.launcher.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * P1-1 语音引擎抽象。语音是 Search 的输入通道，不是第四个 Surface。
 *
 * 默认实现走系统 SpeechRecognizer；无 Google 服务的设备可替换为厂商 SDK
 * （讯飞/百度离线包）实现同一接口，Search 层零改动。
 */
interface SpeechEngine {
    var listener: Listener?

    fun start()
    fun stop()
    fun destroy()

    interface Listener {
        /** 增量结果：必须用它驱动查询，不能等 final。 */
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError()
        fun onListeningChanged(listening: Boolean)
    }

    companion object {
        /** 设备不可用时返回 null（UI 隐藏 🎤）。 */
        fun create(context: Context): SpeechEngine? =
            if (SpeechRecognizer.isRecognitionAvailable(context)) AndroidSpeechEngine(context) else null
    }
}

class AndroidSpeechEngine(private val context: Context) : SpeechEngine {

    private var recognizer: SpeechRecognizer? = null
    override var listener: SpeechEngine.Listener? = null

    override fun start() {
        destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listener?.onListeningChanged(true)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstText()?.let { listener?.onPartial(it) }
            }

            override fun onResults(results: Bundle?) {
                results.firstText()?.let { listener?.onFinal(it) }
                listener?.onListeningChanged(false)
            }

            override fun onError(error: Int) {
                listener?.onError()
                listener?.onListeningChanged(false)
            }

            override fun onEndOfSpeech() = listener?.onListeningChanged(false) ?: Unit
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // onPartialResults 增量
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        )
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun Bundle?.firstText(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
