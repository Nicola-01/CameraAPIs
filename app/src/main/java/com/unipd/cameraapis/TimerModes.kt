package com.unipd.cameraapis

/**
 * Classe che descrive le possibili modalità di autoscatto con i relativi secondi di countdown.
 * @param text Il numero di secondi di countdown. [OFF] indica l'assenza di countdown prima dello scatto di una foto o la registrazione di un
 * video.
 */
enum class TimerModes(val text: String) {
    OFF("OFF"),
    ON_3("3"),
    ON_5("5"),
    ON_10("10");
    companion object {
        /**
         * Seleziona il valore successivo di [text]
         */
        fun next(_current: TimerModes): TimerModes {
            val ordinal = _current.ordinal
            return if(ordinal < TimerModes.values().size - 1) TimerModes.values()[ordinal + 1] else OFF
        }
    }
}