package com.novelai.assistant.data.tts

import android.content.Context
import android.content.Intent
import android.os.Build

data class TtsMediaInfo(
    val bookId: String = "",
    val bookTitle: String = "小说阅读",
    val chapterTitle: String = "正在朗读",
    val chapterIndex: Int = 0,
    val totalChapters: Int = 1,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false
)

interface TtsMediaActionListener {
    fun onPlay()
    fun onPause()
    fun onSkipToNext()
    fun onSkipToPrevious()
    fun onStop()
}

/**
 * 听书全局媒体会话管理器：
 * 桥接 ReaderViewModel / TtsPlayer 与系统通知栏、前台服务、锁屏媒体组件及各类定制系统媒体中心。
 */
object TtsMediaManager {

    var currentInfo: TtsMediaInfo = TtsMediaInfo()
        private set

    private var listener: TtsMediaActionListener? = null

    fun setActionListener(l: TtsMediaActionListener?) {
        this.listener = l
    }

    fun dispatchPlay() { listener?.onPlay() }
    fun dispatchPause() { listener?.onPause() }
    fun dispatchSkipToNext() { listener?.onSkipToNext() }
    fun dispatchSkipToPrevious() { listener?.onSkipToPrevious() }
    fun dispatchStop() { listener?.onStop() }

    /** 开始播放时启动或更新前台媒体服务 */
    fun startOrUpdateService(
        context: Context,
        bookId: String,
        bookTitle: String,
        chapterTitle: String,
        chapterIndex: Int,
        totalChapters: Int,
        isPlaying: Boolean,
        isPaused: Boolean
    ) {
        currentInfo = TtsMediaInfo(
            bookId = bookId,
            bookTitle = bookTitle,
            chapterTitle = chapterTitle,
            chapterIndex = chapterIndex,
            totalChapters = totalChapters,
            isPlaying = isPlaying,
            isPaused = isPaused
        )

        val service = TtsPlaybackService.instance
        if (service != null) {
            // 服务已存活，在进程内安全直接更新，避免后台启动限制
            service.updateNotification()
        } else if (isPlaying) {
            val intent = Intent(context, TtsPlaybackService::class.java).apply {
                action = TtsPlaybackService.ACTION_UPDATE_STATE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 停止听书服务并移除通知栏媒体卡片 */
    fun stopService(context: Context) {
        currentInfo = currentInfo.copy(isPlaying = false, isPaused = false)
        val service = TtsPlaybackService.instance
        if (service != null) {
            service.stopForegroundAndSelf()
        }
    }
}
