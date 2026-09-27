package com.mutkuensert.seslendirmen.feature.reader.data.preferences

import android.content.Context
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsQuality
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : TtsSettingsRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun readNumSteps(): Int = TtsQuality.validatedNumSteps(
        preferences.getInt(KEY_NUM_STEPS, TtsQuality.DEFAULT_NUM_STEPS),
    )

    override fun saveNumSteps(value: Int) {
        preferences.edit()
            .putInt(KEY_NUM_STEPS, TtsQuality.validatedNumSteps(value))
            .apply()
    }

    private companion object {
        private const val PREFERENCES_NAME = "tts_preferences"
        private const val KEY_NUM_STEPS = "num_steps"
    }
}
