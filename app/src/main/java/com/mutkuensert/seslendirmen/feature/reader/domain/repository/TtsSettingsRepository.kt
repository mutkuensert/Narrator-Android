package com.mutkuensert.seslendirmen.feature.reader.domain.repository

import com.mutkuensert.seslendirmen.feature.reader.domain.model.TtsLanguage
import kotlinx.coroutines.flow.StateFlow

interface TtsSettingsRepository {
    val numSteps: StateFlow<Int>
    val language: StateFlow<TtsLanguage>

    fun readNumSteps(): Int
    fun saveNumSteps(value: Int)
    fun readLanguage(): TtsLanguage
    fun saveLanguage(language: TtsLanguage)
}

object TtsQuality {
    const val DEFAULT_NUM_STEPS = 6
    const val MIN_NUM_STEPS = 1
    const val MAX_NUM_STEPS = 20

    fun validatedNumSteps(value: Int): Int {
        return value.coerceIn(MIN_NUM_STEPS, MAX_NUM_STEPS)
    }
}
