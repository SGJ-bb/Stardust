package com.aicompanion.game.logic

/**
 * 游戏时钟(M1) —— 确定性推进"日/时刻/阶段"，供日程调度与存档使用。
 * 纯本地、无网络，与主工程解耦。
 */
class TimeSystem {

    enum class DayPhase { MORNING, AFTERNOON, EVENING, NIGHT }

    var hour = 6
        private set
    var minute = 0
        private set
    var day = 1
        private set

    /** 每 tick(外部以固定节拍调用)推进 5 分钟。 */
    fun tick() {
        minute += 5
        if (minute >= 60) { minute = 0; hour += 1 }
        if (hour >= 24) { hour = 0; day += 1 }
    }

    val phase: DayPhase
        get() = when (hour) {
            in 6..11 -> DayPhase.MORNING
            in 12..17 -> DayPhase.AFTERNOON
            in 18..21 -> DayPhase.EVENING
            else -> DayPhase.NIGHT
        }

    fun restore(hour: Int, minute: Int, day: Int) {
        this.hour = hour
        this.minute = minute
        this.day = day
    }

    override fun toString(): String =
        "Day $day " + String.format("%02d:%02d", hour, minute) + " [$phase]"
}