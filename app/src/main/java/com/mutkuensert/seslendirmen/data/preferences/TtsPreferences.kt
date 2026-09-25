package com.mutkuensert.seslendirmen.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readNumSteps(): Int = preferences
        .getInt(KEY_NUM_STEPS, DEFAULT_NUM_STEPS)
        .coerceIn(MIN_NUM_STEPS, MAX_NUM_STEPS)

    fun saveNumSteps(value: Int) {
        preferences.edit()
            .putInt(KEY_NUM_STEPS, value.coerceIn(MIN_NUM_STEPS, MAX_NUM_STEPS))
            .apply()
    }

    companion object {
        const val DEFAULT_NUM_STEPS = 6
        const val MIN_NUM_STEPS = 1
        const val MAX_NUM_STEPS = 20

        private const val PREFERENCES_NAME = "tts_preferences"
        private const val KEY_NUM_STEPS = "num_steps"
    }
}
