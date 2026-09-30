package com.novelai.assistant.data.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.novelai.assistant.MainActivity
import com.novelai.assistant.R

/**
 * 听书前台媒体服务：
 * 负责接入 Android 原生及定制系统（MIUI/HyperOS, ColorOS, OriginOS, OneUI 等）媒体卡片组件，
 * 提供后台播放保活、锁屏控制器、通知栏大号媒体卡片与控制按钮（上一章、播放/暂停、下一章、关闭）。
 */
class TtsPlaybackService : Service() {

    private var mediaSession: MediaSessionCompat? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 申请 WakeLock，保障锁屏熄屏时持续朗读
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NovelAI:TtsWakeLock").apply {
            setReferenceCounted(false)
        }

        // 初始化 MediaSession
        mediaSession = MediaSessionCompat(this, "NovelAiTtsSession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setPlaybackToLocal(android.media.AudioManager.STREAM_MUSIC)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    TtsMediaManager.dispatchPlay()
                }

                override fun onPause() {
                    TtsMediaManager.dispatchPause()
                }

                override fun onSkipToNext() {
                    TtsMediaManager.dispatchSkipToNext()
                }

                override fun onSkipToPrevious() {
                    TtsMediaManager.dispatchSkipToPrevious()
                }

                override fun onStop() {
                    TtsMediaManager.dispatchStop()
                }
            })
            isActive = true
        }

        // 必须在 onCreate 中立即启动前台通知，避免系统 5 秒超前后台杀除限制
        val info = TtsMediaManager.currentInfo
        val notification = buildNotification(info)
        startForegroundCompat(notification)
        updatePlaybackState(info)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_PLAY -> TtsMediaManager.dispatchPlay()
            ACTION_PAUSE -> TtsMediaManager.dispatchPause()
            ACTION_PREVIOUS -> TtsMediaManager.dispatchSkipToPrevious()
            ACTION_NEXT -> TtsMediaManager.dispatchSkipToNext()
            ACTION_STOP -> {
                TtsMediaManager.dispatchStop()
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATE, null -> {
                updateNotification()
            }
        }
        return START_NOT_STICKY
    }

    fun updateNotification() {
        val info = TtsMediaManager.currentInfo
        if (info.isPlaying) {
            try {
                wakeLock?.acquire(30 * 60 * 1000L)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val notification = buildNotification(info)
            notificationManager.notify(NOTIFICATION_ID, notification)
            updatePlaybackState(info)
        } else {
            stopForegroundAndSelf()
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updatePlaybackState(info: TtsMediaInfo) {
        val session = mediaSession ?: return

        // 1. 设置元数据
        val meta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, info.chapterTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, info.bookTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "AI 智阅小说 · 听书")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L)
            .build()
        session.setMetadata(meta)

        // 2. 设置播放器状态与可用动作
        val state = if (info.isPaused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        val stateCompat = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        session.setPlaybackState(stateCompat)
    }

    private fun buildNotification(info: TtsMediaInfo): Notification {
        val sessionToken = mediaSession?.sessionToken

        // 点击通知卡片回到阅读器
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_BOOK_ID", info.bookId)
        }
        val pendingContent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 动作按钮 Intent
        val prevPending = createActionPendingIntent(ACTION_PREVIOUS, 1)
        val playPausePending = if (info.isPaused) {
            createActionPendingIntent(ACTION_PLAY, 2)
        } else {
            createActionPendingIntent(ACTION_PAUSE, 2)
        }
        val nextPending = createActionPendingIntent(ACTION_NEXT, 3)
        val stopPending = createActionPendingIntent(ACTION_STOP, 4)

        // MediaStyle 样式配置（紧凑模式展示 3 个动作：上一章、播放/暂停、下一章）
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPending)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts_notification)
            .setContentTitle(info.chapterTitle)
            .setContentText(info.bookTitle)
            .setSubText("AI 智阅小说 · 听书中")
            .setContentIntent(pendingContent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(!info.isPaused)
            .setStyle(mediaStyle)
            // 动作：0: 上一章, 1: 播放/暂停, 2: 下一章, 3: 关闭
            .addAction(
                android.R.drawable.ic_media_previous,
                "上一章",
                prevPending
            )
            .addAction(
                if (info.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (info.isPaused) "播放" else "暂停",
                playPausePending
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "下一章",
                nextPending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                stopPending
            )
            .build()
    }

    private fun createActionPendingIntent(actionStr: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TtsPlaybackService::class.java).apply {
            action = actionStr
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "听书播放与系统媒体控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "在通知栏与控制中心展示听书媒体卡片，支持上一章/下一章/播放暂停等控制"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun stopForegroundAndSelf() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        stopForegroundAndSelf()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var instance: TtsPlaybackService? = null
            private set

        const val CHANNEL_ID = "novelai_tts_playback_channel"
        const val NOTIFICATION_ID = 2026

        const val ACTION_PLAY = "com.novelai.assistant.action.TTS_PLAY"
        const val ACTION_PAUSE = "com.novelai.assistant.action.TTS_PAUSE"
        const val ACTION_PREVIOUS = "com.novelai.assistant.action.TTS_PREVIOUS"
        const val ACTION_NEXT = "com.novelai.assistant.action.TTS_NEXT"
        const val ACTION_STOP = "com.novelai.assistant.action.TTS_STOP"
        const val ACTION_UPDATE_STATE = "com.novelai.assistant.action.TTS_UPDATE_STATE"

        const val EXTRA_BOOK_ID = "EXTRA_BOOK_ID"
        const val EXTRA_BOOK_TITLE = "EXTRA_BOOK_TITLE"
        const val EXTRA_CHAPTER_TITLE = "EXTRA_CHAPTER_TITLE"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_IS_PAUSED = "EXTRA_IS_PAUSED"
    }
}
