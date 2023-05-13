package com.unipd.cameraapis

enum class TimerModes(val text: String) {
    OFF("OFF"),
    ON_2("2"),
    ON_5("5"),
    ON_10("10");
    companion object {
        fun next(_current: TimerModes): TimerModes {
            val ordinal = _current.ordinal
            return if(ordinal < TimerModes.values().size - 1) TimerModes.values()[ordinal + 1] else OFF
        }
    }
}