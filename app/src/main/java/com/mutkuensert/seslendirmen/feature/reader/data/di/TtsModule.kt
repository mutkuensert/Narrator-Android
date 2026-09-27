package com.mutkuensert.seslendirmen.feature.reader.data.di

import com.mutkuensert.seslendirmen.feature.reader.data.playback.AudioTrackPlayer
import com.mutkuensert.seslendirmen.feature.reader.data.playback.AndroidPlaybackServiceController
import com.mutkuensert.seslendirmen.feature.reader.data.playback.BufferedTtsPlaybackController
import com.mutkuensert.seslendirmen.feature.reader.data.tts.LocaleSentenceChunker
import com.mutkuensert.seslendirmen.feature.reader.data.tts.SherpaOnnxTtsEngine
import com.mutkuensert.seslendirmen.feature.reader.data.preferences.TtsPreferences
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.AudioPlayer
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.PlaybackServiceController
import com.mutkuensert.seslendirmen.feature.reader.domain.playback.TtsPlaybackController
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.SpeechChunker
import com.mutkuensert.seslendirmen.feature.reader.domain.repository.TtsSettingsRepository
import com.mutkuensert.seslendirmen.feature.reader.domain.tts.TtsEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TtsModule {
    @Binds
    @Singleton
    abstract fun bindTtsEngine(implementation: SherpaOnnxTtsEngine): TtsEngine

    @Binds
    @Singleton
    abstract fun bindAudioPlayer(implementation: AudioTrackPlayer): AudioPlayer

    @Binds
    @Singleton
    abstract fun bindPlaybackController(
        implementation: BufferedTtsPlaybackController,
    ): TtsPlaybackController

    @Binds
    @Singleton
    abstract fun bindSpeechChunker(implementation: LocaleSentenceChunker): SpeechChunker

    @Binds
    @Singleton
    abstract fun bindPlaybackServiceController(
        implementation: AndroidPlaybackServiceController,
    ): PlaybackServiceController

    @Binds
    @Singleton
    abstract fun bindTtsSettingsRepository(
        implementation: TtsPreferences,
    ): TtsSettingsRepository
}
