package com.unipd.cameraapis

import androidx.camera.core.ImageCapture.FlashMode

enum class FlashModes(val text: String) {
    OFF("OFF"),
    ON("ON"),
    AUTO("AUTO");
    companion object {
        fun next(_current: FlashModes): FlashModes {
            val ordinal = _current.ordinal
            return if(ordinal < FlashModes.values().size - 1) FlashModes.values()[ordinal + 1] else OFF
        }
    }
}