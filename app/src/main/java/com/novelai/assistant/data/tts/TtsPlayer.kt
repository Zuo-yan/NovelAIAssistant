package com.novelai.assistant.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 听书播放器：封装 Android 系统 TTS 引擎。
 * 按段落队列朗读，读毕回调供上层自动连播下一章。
 */
@Singleton
class TtsPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var initOk = false

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** 当前朗读到的段落序号（章节内） */
    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    /** 朗读语速倍率（0.5 ~ 3.0） */
    private val _rate = MutableStateFlow(1.0f)
    val rate: StateFlow<Float> = _rate.asStateFlow()

    private var queue: List<String> = emptyList()
    private var queueIndex = 0
    private var chapterEndCallback: (() -> Unit)? = null
    private var lastUtteranceId = 0

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        tts = TextToSpeech(context) { status ->
            initOk = status == TextToSpeech.SUCCESS
            if (initOk) {
                // 设置音频流属性为 USAGE_MEDIA，支持锁屏与后台持续发声
                val audioAttrs = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
                tts?.setAudioAttributes(audioAttrs)

                // 中文优先，失败则退回系统默认语言
                val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(1.05f)
            }
            _ready.value = initOk
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
                _paused.value = false
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post { speakNext() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { speakNext() }
            }
        })
    }

    /** 调节语速，立即生效（正在朗读时从当前段重读） */
    fun setRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 3.0f)
        _rate.value = clamped
        tts?.setSpeechRate(clamped)
        if (_speaking.value && !_paused.value) speakCurrent()
    }

    /** 开始朗读一个段落序列；onChapterEnd 在整章读完后于主线程回调 */
    fun play(paragraphs: List<String>, startIndex: Int = 0, onChapterEnd: () -> Unit) {
        if (!initOk) return
        chapterEndCallback = onChapterEnd
        queue = paragraphs.drop(startIndex).flatMap { splitLongParagraph(it) }
        queueIndex = 0
        _position.value = startIndex
        _paused.value = false
        speakCurrent()
    }

    private fun speakCurrent() {
        val engine = tts ?: return
        if (queueIndex >= queue.size) {
            _speaking.value = false
            chapterEndCallback?.invoke()
            return
        }
        lastUtteranceId++
        engine.speak(queue[queueIndex], TextToSpeech.QUEUE_FLUSH, null, "utt_$lastUtteranceId")
        _speaking.value = true
    }

    private fun speakNext() {
        if (_paused.value) return
        queueIndex++
        // 段落级位置回推（队列被 splitLongParagraph 展开过，仅作近似指示）
        _position.value += 1
        speakCurrent()
    }

    /** 暂停：停止当前 utterance，恢复时从当前段重读 */
    fun pause() {
        if (!_speaking.value) return
        _paused.value = true
        tts?.stop()
        _speaking.value = false
    }

    fun resume() {
        if (!_paused.value) return
        _paused.value = false
        speakCurrent()
    }

    fun stop() {
        _paused.value = false
        _speaking.value = false
        queue = emptyList()
        queueIndex = 0
        chapterEndCallback = null
        tts?.stop()
    }

    /** 释放引擎（Activity 销毁时调用） */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        initOk = false
        _ready.value = false
    }

    companion object {
        private const val MAX_CHUNK = 2000

        /** 超长段落按句子边界切块，避免超过 TTS 4000 字符上限 */
        fun splitLongParagraph(text: String): List<String> {
            if (text.length <= MAX_CHUNK) return listOf(text)
            val chunks = mutableListOf<String>()
            val sentences = text.split(Regex("(?<=[。！？!?；;\n])"))
            var buf = StringBuilder()
            for (s in sentences) {
                if (buf.length + s.length > MAX_CHUNK && buf.isNotEmpty()) {
                    chunks.add(buf.toString())
                    buf = StringBuilder()
                }
                buf.append(s)
            }
            if (buf.isNotEmpty()) chunks.add(buf.toString())
            return chunks.ifEmpty { listOf(text) }
        }
    }
}
