package com.mutkuensert.seslendirmen.feature.reader.domain.repository

interface TtsSettingsRepository {
    fun readNumSteps(): Int
    fun saveNumSteps(value: Int)
}

object TtsQuality {
    const val DEFAULT_NUM_STEPS = 6
    const val MIN_NUM_STEPS = 1
    const val MAX_NUM_STEPS = 20

    fun validatedNumSteps(value: Int): Int {
        return value.coerceIn(MIN_NUM_STEPS, MAX_NUM_STEPS)
    }
}
