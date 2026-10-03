package com.novelai.assistant.data.tts

/** 听书定时模式：收敛在全局 TtsPlayer 中，离开阅读页后依然生效 */
enum class TtsTimerMode(val label: String, val minutes: Int) {
    OFF("不设置（听到全书完）", 0),
    END_OF_CHAPTER("听完本章停止", -1),
    MIN_15("15 分钟", 15),
    MIN_20("20 分钟", 20),
    MIN_30("30 分钟", 30),
    MIN_45("45 分钟", 45),
    MIN_60("60 分钟", 60),
    MIN_90("90 分钟", 90)
}
