package com.novelai.assistant.data.tts

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 通知栏媒体卡片点击 → 打开听书模块的单次导航事件总线 */
@Singleton
class ListeningNavBus @Inject constructor() {

    private val _openListening = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 8)
    val openListening: SharedFlow<Unit> = _openListening

    fun requestOpenListening() {
        _openListening.tryEmit(Unit)
    }
}
