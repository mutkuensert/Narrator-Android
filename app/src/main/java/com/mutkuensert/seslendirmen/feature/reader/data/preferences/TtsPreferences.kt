package com.mutkuensert.seslendirmen.feature.reader.data.preferences

import android.content.Context
import com.mutkuensert.seslendirmen.feature.reader.domain.model.TtsLanguage
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsQuality
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : TtsSettingsRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val deviceDefaultLanguage = TtsLanguage.defaultForDeviceLanguage(
        context.resources.configuration.locales[0].language,
    )
    private val _numSteps = MutableStateFlow(
        TtsQuality.validatedNumSteps(
            preferences.getInt(KEY_NUM_STEPS, TtsQuality.DEFAULT_NUM_STEPS),
        ),
    )
    override val numSteps: StateFlow<Int> = _numSteps.asStateFlow()
    private val _language = MutableStateFlow(
        TtsLanguage.fromCode(preferences.getString(KEY_LANGUAGE, null)) ?: deviceDefaultLanguage,
    )
    override val language: StateFlow<TtsLanguage> = _language.asStateFlow()

    init {
        if (!preferences.contains(KEY_LANGUAGE)) saveLanguage(deviceDefaultLanguage)
    }

    override fun readNumSteps(): Int = numSteps.value

    override fun saveNumSteps(value: Int) {
        val validatedValue = TtsQuality.validatedNumSteps(value)
        preferences.edit()
            .putInt(KEY_NUM_STEPS, validatedValue)
            .apply()
        _numSteps.value = validatedValue
    }

    override fun readLanguage(): TtsLanguage = language.value

    override fun saveLanguage(language: TtsLanguage) {
        preferences.edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
        _language.value = language
    }

    private companion object {
        private const val PREFERENCES_NAME = "tts_preferences"
        private const val KEY_NUM_STEPS = "num_steps"
        private const val KEY_LANGUAGE = "language"
    }
}
