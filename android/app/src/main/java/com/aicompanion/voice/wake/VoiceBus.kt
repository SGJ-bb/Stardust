/** 语音占用标记：ASR / TTS 期间暂停唤醒监听，避免自己听到自己。 */
package com.aicompanion.voice.wake

/** 进程内共享的「麦克风/扬声器是否忙」状态。 */
object VoiceBus {

    /** 正在做语音识别（LocalAsrManager 录音中） */
    @Volatile
    var asrActive: Boolean = false

    /** 正在朗读（TTS 播放中） */
    @Volatile
    var ttsActive: Boolean = false

    /** 唤醒后到开始录音之间的短暂静默，防止唤醒词尾巴被当成说话内容 */
    @Volatile
    var wakeSettleUntil: Long = 0L

    fun busy(): Boolean =
        asrActive || ttsActive || System.currentTimeMillis() < wakeSettleUntil
}
