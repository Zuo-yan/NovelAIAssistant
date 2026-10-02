package com.novelai.assistant.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * 听书专用音频焦点管理器：
 * 确保应用在后台或锁屏熄屏朗读时持有系统的媒体音频焦点 (AUDIOFOCUS_GAIN)，
 * 避免系统电源策略或省电机制因没有活动焦点而切断或挂起音频服务。
 * 同时与来电、导航语音等系统音频事件优雅协同（临时避让与自动恢复）。
 */
class TtsAudioFocusManager(
    private val context: Context,
    private val onLoss: (isTransient: Boolean) -> Unit,
    private val onGain: () -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                onLoss(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasFocus = false
                onLoss(true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                onGain()
            }
        }
    }

    /** 申请音频焦点 */
    fun requestFocus(): Boolean {
        val am = audioManager ?: return false
        if (hasFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttrs)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()

            focusRequest = request
            am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasFocus
    }

    /** 释放音频焦点 */
    fun abandonFocus() {
        val am = audioManager ?: return
        if (!hasFocus && focusRequest == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusChangeListener)
        }
        hasFocus = false
    }
}
