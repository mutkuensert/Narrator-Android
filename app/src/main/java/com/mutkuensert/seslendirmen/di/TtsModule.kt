package com.mutkuensert.seslendirmen.di

import com.mutkuensert.seslendirmen.data.playback.AudioTrackPlayer
import com.mutkuensert.seslendirmen.data.tts.SherpaOnnxTtsEngine
import com.mutkuensert.seslendirmen.domain.playback.AudioPlayer
import com.mutkuensert.seslendirmen.domain.tts.TtsEngine
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

}
