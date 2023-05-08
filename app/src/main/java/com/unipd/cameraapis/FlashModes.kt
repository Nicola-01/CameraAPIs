package com.unipd.cameraapis

import androidx.camera.core.ImageCapture.FlashMode

enum class FlashModes {
    OFF,
    ON,
    AUTO;
    companion object {
        fun next(_current: FlashModes): FlashModes {
            val ordinal = _current.ordinal
            return FlashModes.values()[ordinal + 1]
        }
    }
}